import boto3
import urllib3
import json
import os

table_name = os.environ.get("TABLE_NAME")
region = os.environ.get("REGION")
endpoint = os.environ.get("ENDPOINT")
http = urllib3.PoolManager()
dynamodb = boto3.resource("dynamodb", region_name=region)
table = dynamodb.Table(table_name)

def query_active_users():
    userObjects = table.scan(ProjectionExpression = "username")["Items"]
    userList = []
    for user in userObjects:
        userList.append(user["username"])
    return userList

def lambda_handler(event, context):
    userList = query_active_users()
    data = {'usernames': userList}
    encoded_data = json.dumps(data).encode('utf-8')

    resp = http.request(
        'POST',
        endpoint,
        body=encoded_data,
        headers={'Content-Type': 'application/json'})

    data = json.loads(resp.data.decode('utf-8'))['json']
    return data
