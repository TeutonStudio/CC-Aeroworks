#!/usr/bin/env python3
"""Validate Wiki navigation, language entry points, media paths and capture coverage."""
from pathlib import Path
import argparse
import json
import re
import struct
from urllib.parse import unquote

ROOT = Path(__file__).resolve().parents[1]
WIKI = ROOT / 'wiki'
MEDIA_PREFIX = 'https://raw.githubusercontent.com/wiki/TeutonStudio/CC-Aeroworks/'


def slug(value):
    value = re.sub(r'[`*\[\]]', '', value).lower().strip()
    return ''.join(c for c in value if c.isalnum() or c in ' -_').replace(' ', '-')


def verify(require_all=False):
    errors = []
    captures = set()
    for page in sorted(WIKI.glob('*.md')):
        text = page.read_text()
        for target in re.findall(r'\[\[([^\]]+)\]\]', text):
            target = target.rsplit('|', 1)[-1].strip()
            name, _, anchor = target.partition('#')
            destination = WIKI / ((name or page.stem) + '.md')
            if not destination.is_file():
                errors.append(f'{page.name}: missing Wiki page {target}')
            elif anchor:
                anchors = {slug(h) for h in re.findall(r'^#+ (.+)$', destination.read_text(), re.M)}
                if anchor not in anchors:
                    errors.append(f'{page.name}: missing Wiki anchor {target}')
        for target in re.findall(r"(?<!!)\[[^\]]+\]\(([^)]+)\)", text):
            if "://" in target or target.startswith("mailto:"):
                continue
            name, _, anchor = target.partition("#")
            destination = WIKI / ((name.removesuffix(".md") or page.stem) + ".md")
            if not destination.is_file():
                errors.append(f"{page.name}: missing Markdown page {target}")
            elif anchor and anchor not in {slug(h) for h in re.findall(r"^#+ (.+)$", destination.read_text(), re.M)}:
                errors.append(f"{page.name}: missing Markdown anchor {target}")
        for url in re.findall(r'!\[[^\]]+\]\(([^)]+)\)', text):
            if not url.startswith(MEDIA_PREFIX):
                errors.append(f'{page.name}: unsupported media destination {url}')
                continue
            relative = unquote(url[len(MEDIA_PREFIX):])
            file = WIKI / relative
            if not file.is_file():
                errors.append(f'{page.name}: missing media {relative}')
                continue
            header = file.read_bytes()[:24]
            if header.startswith(b'\x89PNG\r\n\x1a\n'):
                width, height = struct.unpack('>II', header[16:24])
                if min(width, height) < 100:
                    errors.append(f'{page.name}: unexpectedly small media {relative}')
            elif not header.startswith((b'GIF87a', b'GIF89a')):
                errors.append(f'{page.name}: expected real PNG/GIF {relative}')
            captures.add(relative)
        if page.stem not in {'Home', 'English', '_Sidebar', '_Footer'} and '-en-' not in page.stem:
            if '[[Deutsch|' not in text or '[[English|' not in text:
                errors.append(f'{page.name}: missing reciprocal language navigation')
    readme = (ROOT / 'README.md').read_text()
    if len(readme.split()) > 130 or '/wiki/English' not in readme:
        errors.append('README must be concise and link the English Wiki')
    inventory = json.loads((ROOT / 'docs/wiki-media-coverage.json').read_text())
    pending = []
    for feature in inventory['features']:
        if not (WIKI / (feature['page'] + '.md')).is_file():
            errors.append(f"Unknown feature page: {feature['id']}")
        for medium in feature['media']:
            if medium not in captures:
                errors.append(f"Feature media is absent from published pages: {feature['id']}: {medium}")
        if feature['status'] != 'shown':
            pending.append(feature['id'])
        elif not feature['media']:
            errors.append(f"Feature claims coverage without media: {feature['id']}")
    if errors:
        print('\n'.join('FAIL: ' + e for e in errors))
        return 1
    print(f'PASS: Wiki links, language navigation and {len(captures)} embedded media files.')
    print(f"Feature media: {len(inventory['features']) - len(pending)}/{len(inventory['features'])} shown.")
    if pending:
        print('OPEN: ' + ', '.join(pending))
        if require_all:
            print('FAIL: Complete feature illustration is still pending.')
            return 1
    return 0

if __name__ == '__main__':
    args = argparse.ArgumentParser()
    args.add_argument('--require-all-media', action='store_true')
    raise SystemExit(verify(args.parse_args().require_all_media))
