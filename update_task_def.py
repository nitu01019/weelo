import json
import os
import subprocess
import sys

TASK_DEF_ARN = os.getenv("TASK_DEF_ARN", "")
API_KEY = os.getenv("GOOGLE_MAPS_API_KEY", "")
AWS_REGION = os.getenv("AWS_REGION", "ap-south-1")

def run_command(command):
    result = subprocess.run(command, shell=True, capture_output=True, text=True)
    if result.returncode != 0:
        print(f"Error running command: {command}")
        print(result.stderr)
        sys.exit(1)
    return result.stdout

print(f"Fetching task definition: {TASK_DEF_ARN}")
if not TASK_DEF_ARN:
    print("TASK_DEF_ARN is required. Example: arn:aws:ecs:region:acct:task-definition/name:rev")
    sys.exit(1)
if not API_KEY:
    print("GOOGLE_MAPS_API_KEY is required. Export it before running this script.")
    sys.exit(1)

json_output = run_command(
    f"aws ecs describe-task-definition --task-definition {TASK_DEF_ARN} --region {AWS_REGION}"
)
data = json.loads(json_output)

task_def = data['taskDefinition']

# Clean up fields not needed for registration
keys_to_remove = [
    'taskDefinitionArn',
    'revision',
    'status',
    'requiresAttributes',
    'compatibilities',
    'registeredAt',
    'registeredBy'
]

for key in keys_to_remove:
    if key in task_def:
        del task_def[key]

# Update environment variables
container_defs = task_def.get('containerDefinitions', [])
if not container_defs:
    print("No container definitions found!")
    sys.exit(1)

# Assuming the first container is the main app
container = container_defs[0]
env_vars = container.get('environment', [])

# Remove existing key if present to avoid duplicates
env_vars = [e for e in env_vars if e['name'] != 'GOOGLE_MAPS_API_KEY']

# Add new key
print("Injecting GOOGLE_MAPS_API_KEY...")
env_vars.append({
    'name': 'GOOGLE_MAPS_API_KEY',
    'value': API_KEY
})

container['environment'] = env_vars

# Save to file
with open('new_task_def.json', 'w') as f:
    json.dump(task_def, f, indent=2)

print("Created new_task_def.json successfully.")
