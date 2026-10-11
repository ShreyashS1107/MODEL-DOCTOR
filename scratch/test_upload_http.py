import urllib.request
import urllib.error
import uuid
import json

def post_multipart(url, fields, files):
    boundary = uuid.uuid4().hex
    body = bytearray()
    for k, v in fields.items():
        body.extend(f'--{boundary}\r\nContent-Disposition: form-data; name="{k}"\r\n\r\n{v}\r\n'.encode('utf-8'))
    for k, filepath in files.items():
        filename = filepath.replace('\\', '/').split('/')[-1]
        body.extend(f'--{boundary}\r\nContent-Disposition: form-data; name="{k}"; filename="{filename}"\r\nContent-Type: application/octet-stream\r\n\r\n'.encode('utf-8'))
        with open(filepath, 'rb') as f:
            body.extend(f.read())
        body.extend(b'\r\n')
    body.extend(f'--{boundary}--\r\n'.encode('utf-8'))
    
    req = urllib.request.Request(url, data=bytes(body), headers={
        'Content-Type': f'multipart/form-data; boundary={boundary}',
        'Accept': 'application/json'
    }, method='POST')
    try:
        with urllib.request.urlopen(req) as resp:
            data = resp.read().decode('utf-8')
            print(f'POST {url} -> Status {resp.status}')
            return json.loads(data)
    except urllib.error.HTTPError as e:
        err = e.read().decode('utf-8')
        print(f'POST {url} -> HTTPError {e.code}: {err}')
        return None

print('=== 1. UPLOADING MODEL ===')
model_res = post_multipart('http://localhost:8080/api/artifacts/models',
               {'framework': 'xgboost', 'taskType': 'binary_classification'},
               {'file': r'Diagnostic Data Check/Model Artifact/fraud_model.json'})
print('Model Res:', model_res)

print('\n=== 2. UPLOADING EVAL DATASET ===')
eval_res = post_multipart('http://localhost:8080/api/artifacts/datasets',
               {'format': 'csv'},
               {'file': r'Diagnostic Data Check/Evaluation Dataset/evaluation_dataset.csv'})
print('Eval Res:', eval_res)

print('\n=== 3. UPLOADING BASELINE DATASET ===')
base_res = post_multipart('http://localhost:8080/api/artifacts/datasets',
               {'format': 'csv'},
               {'file': r'Diagnostic Data Check/Baseline Reference Dataset/baseline_dataset.csv'})
print('Base Res:', base_res)

if model_res and eval_res and base_res:
    print('\n=== 4. TESTING PREFLIGHT VALIDATE ===')
    val_payload = {
        'modelArtifactId': model_res['id'],
        'evaluationDatasetArtifactId': eval_res['id'],
        'baselineDatasetArtifactId': base_res['id'],
        'targetColumn': 'is_fraud',
        'predictionColumn': 'pred_prob',
        'protectedAttribute': 'is_foreign_ip'
    }
    req = urllib.request.Request('http://localhost:8080/api/diagnostics/validate',
                                 data=json.dumps(val_payload).encode('utf-8'),
                                 headers={'Content-Type': 'application/json', 'Accept': 'application/json'},
                                 method='POST')
    try:
        with urllib.request.urlopen(req) as resp:
            val_data = json.loads(resp.read().decode('utf-8'))
            print('POST /api/diagnostics/validate -> Status', resp.status)
            print('Validation Valid:', val_data.get('valid'))
            print('Errors:', val_data.get('errors'))
            print('Warnings:', val_data.get('warnings'))
    except urllib.error.HTTPError as e:
        print('Validation HTTPError', e.code, e.read().decode('utf-8'))
