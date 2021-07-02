import os
import logging
import time
import socket
import requests
import boto3

from requests.exceptions import ConnectionError

'''
This code interrogates the ECS agent running on EC2 instances and for each task, compares the `DesiredStatus`
and `KnownStatus`. If for any task, those statuses are different for a period of time defined by 
`iteration_max` * `sleep_seconds`, the EC2 instance is marked as Unhealthy (and therefore purged from ECS).
'''


def setup_logging(logger_level):
    the_logger = logging.getLogger()
    for old_handler in the_logger.handlers:
        the_logger.removeHandler(old_handler)

    new_handler = logging.FileHandler("/var/log/ecs_instance_health_check.log")

    hostname = socket.gethostname()

    json_format = (
        '{{ "timestamp": "%(asctime)s", "log_level": "%(levelname)s", "message": "%(message)s", '
        '"environment": "NOT_SET", "application": "NOT_SET", '
        '"module": "%(module)s", "process": "%(process)s", '
        '"thread": "[%(thread)s]", "hostname": "{hostname}" }} '
    ).format(hostname=hostname)

    new_handler.setFormatter(logging.Formatter(json_format))
    the_logger.addHandler(new_handler)
    new_level = logging.getLevelName(logger_level)
    the_logger.setLevel(new_level)

    if the_logger.isEnabledFor(logging.DEBUG):
        boto3.set_stream_logger()
        the_logger.debug('Using boto3", "version": "%s"' % boto3.__version__)

    return the_logger


logger = setup_logging(logging.INFO)

ecs_agent_url = "http://localhost:51678/v1/tasks"
iteration_max = 5
sleep_seconds = 60


def mark_ecs_instance_as_unhealthy():
    client = boto3.client('autoscaling', region_name=os.environ.get("REGION"))
    response = client.set_instance_health(
        InstanceId=os.environ.get("INSTANCE_ID"),
        HealthStatus="Unhealthy"
    )


def main():
    time.sleep(120)
    logger.info("ECS Instance health check started")
    tasks = {}  # Stores latest version of each ECS tasks returned by ECS agent
    while True:
        try:
            response = requests.get(ecs_agent_url)
        except ConnectionError:
            logger.warning("ECS agent is unreachable. Instance marked as unhealthy.")
            mark_ecs_instance_as_unhealthy()
            return 0

        if response.status_code == 200:  # ECS Agent is responsive
            reponse_json = response.json()
            for task in reponse_json["Tasks"]:
                task_arn = task["Arn"]
                if task_arn not in tasks:  # Is returned task already in dictionnary?
                    tasks[task_arn] = {"count_iteration_with_pending_status": 0}
                if tasks[task_arn]["count_iteration_with_pending_status"] == iteration_max:
                    running_tasks_on_instance = None
                    for task in reponse_json["Tasks"]:
                        if task["KnownStatus"] == "RUNNING":
                            running_tasks_on_instance = True
                            break
                        else:
                            running_tasks_on_instance = False
                    if not running_tasks_on_instance:
                        logger.warning("""Task {0} has been pending for {1} seconds
                         and no other tasks are running  on the instance. Marking instance as unhealthy.""".format(
                            task_arn,
                            iteration_max * sleep_seconds))
                        mark_ecs_instance_as_unhealthy()
                        return 0
                    else:
                        logger.warning("""Task {0} has been pending for {1} seconds
                         but other tasks are running on the instance. Ignoring.""".format(
                            task_arn,
                            iteration_max * sleep_seconds))
                else:
                    logger.info("{0} is healthy".format(task_arn))
                if task["KnownStatus"] == "PENDING":
                    tasks[task_arn]["count_iteration_with_pending_status"] += 1
            logger.info("ECS agent is running")
            time.sleep(sleep_seconds)


if __name__ == "__main__":
    main()
