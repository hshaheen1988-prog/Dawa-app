import json, subprocess, re
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
TYPES = {'p_id':'uuid','p_secret':'text','p_name':'text','p_phone':'text','p_patient_name':'text','p_member':'uuid','p_snapshot':'jsonb',
         'p_marks':'jsonb','p_code':'text','p_since':'timestamptz','p_key':'text','p_status':'text','p_at':'timestamptz','p_keys':'text[]'}
def lit(v, typ):
    if v is None: return f"NULL::{typ}"
    if typ == 'jsonb': return "'" + json.dumps(v, ensure_ascii=False).replace("'", "''") + "'::jsonb"
    if typ == 'text[]': return "ARRAY[" + ",".join("'" + str(x).replace("'", "''") + "'" for x in v) + "]::text[]" if v else "ARRAY[]::text[]"
    return "'" + str(v).replace("'", "''") + f"'::{typ}"
class H(BaseHTTPRequestHandler):
    def log_message(self, *a): pass
    def cors(self):
        self.send_header('Access-Control-Allow-Origin', '*'); self.send_header('Access-Control-Allow-Headers', '*'); self.send_header('Access-Control-Allow-Methods', 'POST, OPTIONS')
    def do_OPTIONS(self): self.send_response(204); self.cors(); self.end_headers()
    def do_POST(self):
        m = re.match(r'^/rest/v1/rpc/(fam_[a-z_]+)$', self.path)
        body = json.loads(self.rfile.read(int(self.headers.get('Content-Length', 0))) or b'{}')
        if not m or self.headers.get('apikey') is None: return self.reply(404, {'message': 'not found'})
        args = ", ".join(f"{k} => {lit(v, TYPES[k])}" for k, v in body.items())
        sql = f"set role anon; select coalesce(to_json(public.{m.group(1)}({args}))::text, 'null');"
        r = subprocess.run(['psql', '-h', '/var/tmp', '-p', '5499', '-U', 'postgres', '-d', 'dawa', '-At', '-q', '-v', 'ON_ERROR_STOP=1', '-c', sql], capture_output=True, text=True)
        if r.returncode: 
            msg = next((l.split('ERROR:')[1].strip() for l in r.stderr.splitlines() if 'ERROR:' in l), r.stderr)
            return self.reply(400, {'message': msg})
        out = r.stdout.strip().splitlines()[-1] if r.stdout.strip() else 'null'
        self.send_response(200); self.cors(); self.send_header('Content-Type', 'application/json'); self.end_headers(); self.wfile.write(out.encode())
    def reply(self, code, obj):
        self.send_response(code); self.cors(); self.send_header('Content-Type', 'application/json'); self.end_headers(); self.wfile.write(json.dumps(obj).encode())
ThreadingHTTPServer(('127.0.0.1', 8791), H).serve_forever()
