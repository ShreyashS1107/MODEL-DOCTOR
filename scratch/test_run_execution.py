import urllib.request
import json
import time

run_id = 'run_b67a2aeb'
print(f'Starting run {run_id}...')
req = urllib.request.Request(f'http://localhost:8080/api/diagnostics/{run_id}/run',
                             headers={'Accept': 'application/json'},
                             method='POST')
try:
    with urllib.request.urlopen(req) as resp:
        print('POST /run Status:', resp.status)
        data = json.loads(resp.read().decode('utf-8'))
        print('Run Status:', data.get('status'))
except Exception as e:
    print('Failed to start run:', e)

# Poll for completion
for i in range(30):
    time.sleep(2)
    req = urllib.request.Request(f'http://localhost:8080/api/diagnostics/{run_id}',
                                 headers={'Accept': 'application/json'})
    try:
        with urllib.request.urlopen(req) as resp:
            data = json.loads(resp.read().decode('utf-8'))
            curr_status = data.get('status')
            print(f'[{(i+1)*2}s] Status: {curr_status}')
            if curr_status in ['COMPLETED', 'FAILED', 'PARTIAL']:
                if curr_status != 'COMPLETED':
                    print('Error message:', data.get('errorMessage'))
                break
    except Exception as e:
        print('Poll error:', e)

# Check investigations
req = urllib.request.Request(f'http://localhost:8080/api/diagnostics/{run_id}/investigations',
                             headers={'Accept': 'application/json'})
with urllib.request.urlopen(req) as resp:
    invs = json.loads(resp.read().decode('utf-8'))
    print('Investigations count:', len(invs))
    for inv in invs:
        print('  Target:', inv.get('targetName') or inv.get('title') or inv.get('id'), '| Type:', inv.get('primaryFailureCategory'))

# Check remediations
req = urllib.request.Request(f'http://localhost:8080/api/diagnostics/{run_id}/remediations',
                             headers={'Accept': 'application/json'})
with urllib.request.urlopen(req) as resp:
    rems = json.loads(resp.read().decode('utf-8'))
    print('Remediations count:', len(rems))
    for rem in rems:
        print('  Candidate:', rem.get('title') or rem.get('remediationType') or rem.get('id'), '| Impact:', rem.get('expectedImpactScore'))
