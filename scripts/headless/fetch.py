#!/usr/bin/env python3
"""Fetches what a headless Baritone client needs and Gradle does not leave lying around.

  - the vanilla client jar and its Linux libraries, from Mojang's version manifest
  - the asset index and every asset object except the sounds (the title screen's panorama is an asset;
    the sounds are not needed and are four fifths of the weight)
  - the Fabric Loader's libraries, from Fabric's launcher profile

usage: fetch.py [--dir DIR] [--minecraft VERSION] [--loader VERSION]

The versions default to the ones pinned in gradle.properties, DIR to $BARITONE_HEADLESS or
~/baritone-headless. Mojang's files are checked against the hash they were published with, and files
that are already there are not fetched again. See README.md in this directory.
"""
import argparse
import hashlib
import json
import os
import re
import sys
import urllib.request
from concurrent.futures import ThreadPoolExecutor

REPO = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..'))
MAVEN_CENTRAL = 'https://repo.maven.apache.org/maven2/'


def properties():
    found = {}
    with open(os.path.join(REPO, 'gradle.properties')) as f:
        for line in f:
            m = re.match(r'^\s*([\w.]+)\s*=\s*(.*?)\s*$', line)
            if m:
                found[m.group(1)] = m.group(2)
    return found


def fetch_json(url):
    with urllib.request.urlopen(url, timeout=60) as r:
        return json.load(r)


def on_linux(library):
    allowed = 'rules' not in library
    for rule in library.get('rules', []):
        if rule.get('os', {}).get('name') in (None, 'linux'):
            allowed = rule['action'] == 'allow'
    return allowed


def download(job):
    dest, urls, sha1 = job
    if os.path.exists(dest):
        if sha1 is None or hashlib.sha1(open(dest, 'rb').read()).hexdigest() == sha1:
            return 'cached'
    os.makedirs(os.path.dirname(dest), exist_ok=True)
    for url in urls:
        for _ in range(3):
            try:
                with urllib.request.urlopen(url, timeout=60) as r:
                    data = r.read()
            except Exception:
                continue
            if sha1 is None or hashlib.sha1(data).hexdigest() == sha1:
                with open(dest, 'wb') as f:
                    f.write(data)
                return 'ok'
    return 'FAILED ' + urls[0]


def run(jobs, label):
    with ThreadPoolExecutor(12) as pool:
        results = list(pool.map(download, jobs))
    failed = [r for r in results if r.startswith('FAILED')]
    print('%-12s %4d files: %d fetched, %d already there, %d failed'
          % (label, len(results), results.count('ok'), results.count('cached'), len(failed)))
    if failed:
        sys.exit('\n'.join(failed[:5]))


def main():
    pins = properties()
    ap = argparse.ArgumentParser()
    ap.add_argument('--dir', default=os.environ.get('BARITONE_HEADLESS', os.path.expanduser('~/baritone-headless')))
    ap.add_argument('--minecraft', default=pins['minecraft_version'])
    ap.add_argument('--loader', default=pins['fabric_version'])
    args = ap.parse_args()
    root = os.path.abspath(args.dir)

    manifest = fetch_json('https://piston-meta.mojang.com/mc/game/version_manifest_v2.json')
    entry = next((v for v in manifest['versions'] if v['id'] == args.minecraft), None)
    if entry is None:
        sys.exit('Minecraft %s is not in the version manifest' % args.minecraft)
    version = fetch_json(entry['url'])

    client = version['downloads']['client']
    jobs = [(root + '/client.jar', [client['url']], client['sha1'])]
    classpath = [root + '/client.jar']
    for lib in version['libraries']:
        artifact = lib['downloads'].get('artifact')
        if artifact and on_linux(lib):
            dest = root + '/libs/' + artifact['path'].replace('/', '__')
            jobs.append((dest, [artifact['url']], artifact['sha1']))
            classpath.append(dest)
    run(jobs, 'client')

    index = version['assetIndex']
    index_path = '%s/assets/indexes/%s.json' % (root, index['id'])
    run([(index_path, [index['url']], index['sha1'])], 'asset index')
    with open(index_path) as f:
        objects = json.load(f)['objects']
    wanted = []
    for name, obj in objects.items():
        if not name.endswith('.ogg'):
            h = obj['hash']
            wanted.append(('%s/assets/objects/%s/%s' % (root, h[:2], h), ['https://resources.download.minecraft.net/%s/%s' % (h[:2], h)], h))
    run(wanted, 'assets')

    profile = fetch_json('https://meta.fabricmc.net/v2/versions/loader/%s/%s/profile/json' % (args.minecraft, args.loader))
    fabric_jobs = []
    fabric_classpath = []
    for lib in profile['libraries']:
        group, artifact, ver = lib['name'].split(':')
        rel = '%s/%s/%s/%s-%s.jar' % (group.replace('.', '/'), artifact, ver, artifact, ver)
        dest = '%s/fabric-libs/%s-%s.jar' % (root, artifact, ver)
        fabric_jobs.append((dest, [lib.get('url', 'https://maven.fabricmc.net/').rstrip('/') + '/' + rel, MAVEN_CENTRAL + rel], None))
        fabric_classpath.append(dest)
    run(fabric_jobs, 'fabric')

    for name, text in (('classpath.txt', ':'.join(classpath)), ('fabric-classpath.txt', ':'.join(fabric_classpath)),
                       ('asset-index.txt', index['id']), ('minecraft-version.txt', args.minecraft)):
        with open(os.path.join(root, name), 'w') as f:
            f.write(text)
    print('ready in', root)


if __name__ == '__main__':
    main()
