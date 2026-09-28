#!/usr/bin/env python3
"""Generate Android Firebase resources; never copy input credentials into this repo."""
import argparse
import json
from pathlib import Path
import sys
import xml.etree.ElementTree as ET


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('google_services_json', type=Path)
    parser.add_argument('target', nargs='?', type=Path, default=Path(__file__).resolve().parents[2] / 'build/element-x-android')
    args = parser.parse_args()
    data = json.loads(args.google_services_json.read_text())
    info = data['project_info']
    clients = {c['client_info']['android_client_info']['package_name']: c for c in data['client']}
    package = 'com.twoecure.messenger'
    if package not in clients:
        sys.exit(f'JSON must include a Firebase Android client for {package}')
    resource_dir = args.target / 'libraries/pushproviders/firebase/src'
    if not all((resource_dir / variant / 'res/values/firebase.xml').is_file()
               for variant in ('main', 'release', 'debug', 'nightly')):
        sys.exit('Target is not a patched Android clone; run apply.sh first')

    def values(client):
        web = next((o['client_id'] for o in client.get('oauth_client', []) if o['client_type'] == 3), '')
        key = client['api_key'][0]['current_key']
        # Missing optional services intentionally become empty, never an upstream value.
        return {
            'default_web_client_id': web,
            'firebase_database_url': info.get('firebase_url', ''),
            'gcm_defaultSenderId': str(info['project_number']),
            'google_api_key': key,
            'google_crash_reporting_api_key': key,
            'google_storage_bucket': info.get('storage_bucket', ''),
            'project_id': info['project_id'],
            'google_app_id': client['client_info']['mobilesdk_app_id'],
        }

    outputs = {}
    for variant in ('main', 'release', 'debug', 'nightly'):
        variant_package = package + ('.' + variant if variant in ('debug', 'nightly') else '')
        client = clients.get(variant_package, clients[package])
        if variant_package not in clients:
            print(f'{variant}: reusing release Firebase values; register {variant_package} for independent device testing')
        entries = values(client)
        for required in ('gcm_defaultSenderId', 'google_api_key', 'project_id', 'google_app_id'):
            if not entries[required] or 'REPLACE_ME' in entries[required]:
                sys.exit(f'Missing {required} for {variant_package}')
        xml = ET.Element('resources')
        for name, value in entries.items():
            ET.SubElement(xml, 'string', name=name, translatable='false').text = value
        ET.indent(xml, space='    ')
        outputs[resource_dir / variant / 'res/values/firebase.xml'] = ET.tostring(xml, encoding='unicode')
    # Validate all variants before modifying any files. Do not log values.
    for path, xml in outputs.items():
        path.write_text('<?xml version="1.0" encoding="utf-8"?>\n' + xml + '\n')
    print('Configured all four firebase.xml files (values not logged). Keep them out of exported patches.')


if __name__ == '__main__':
    try:
        main()
    except (KeyError, IndexError, ValueError, OSError) as exc:
        sys.exit(f'Invalid Firebase configuration: {type(exc).__name__}; check JSON structure and target path')
