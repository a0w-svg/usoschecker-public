#!/usr/bin/env python3
"""Refresh the public API reference bundled with the app. No credentials are sent."""
import concurrent.futures
import json
from pathlib import Path
import time
import urllib.parse
import urllib.request

BASE = 'https://usosapps.vistula.edu.pl/'
DEST = Path(__file__).resolve().parents[1] / 'app/src/main/assets/api'

def read(path):
    with urllib.request.urlopen(BASE + path, timeout=30) as response:
        return json.load(response)

def method(entry):
    name = entry['name']
    for attempt in range(3):
        try:
            data = read('services/apiref/method?' + urllib.parse.urlencode({'name': name}))
            assert data['name'] == name
            target = DEST / (name.replace('/', '__') + '.json')
            target.write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n')
            time.sleep(0.25)
            return name
        except Exception:
            if attempt == 2:
                raise
            time.sleep(2 * (attempt + 1))

def main():
    DEST.mkdir(parents=True, exist_ok=True)
    entries = read('services/apiref/method_index')
    # Two simultaneous public requests; fail the refresh on any missing method.
    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
        for count, _ in enumerate(pool.map(method, entries), 1):
            if count % 50 == 0:
                print(f'{count}/{len(entries)} methods', flush=True)
    (DEST / 'index.json').write_text(json.dumps(entries, ensure_ascii=False, indent=2) + '\n')
    print(f'Complete: {len(entries)} methods', flush=True)

if __name__ == '__main__':
    main()
