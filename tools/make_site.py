#!/usr/bin/env python3
"""site/template.html と site/releases.json から site/index.html を生成する。

releases.json の各要素:
  {"version": "1.0.0", "url": "https://.../helper-1.0.0.apk", "date": "2026-10-01"}
チェックサムとサイズは dist/helper-<version>.checksums.json から取り込む（手入力しない）。
"""
import json, os, sys, datetime

root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
tpl = open(os.path.join(root, 'site', 'template.html'), encoding='utf-8').read()
releases = json.load(open(os.path.join(root, 'site', 'releases.json'), encoding='utf-8'))

out = []
for r in releases:
    cs_path = os.path.join(root, 'dist', f"helper-{r['version']}.checksums.json")
    if not os.path.exists(cs_path):
        sys.exit(f"missing {cs_path} — ./build.sh を先に実行してください")
    cs = json.load(open(cs_path, encoding='utf-8'))
    out.append({
        'version': r['version'],
        'url': r['url'],
        'date': r.get('date', ''),
        'size': cs['size'],
        'sha256': cs['sha256_hex'],
        'package_checksum': cs['package_checksum'],
        'signature_checksum': cs['signature_checksum'],
    })

html = (tpl.replace('__RELEASES__', json.dumps(out, ensure_ascii=False, indent=2))
           .replace('__COMPONENT__', 'jp.initialsetup.helper/.AdminReceiver')
           .replace('__UPDATED__', datetime.date.today().isoformat()))
open(os.path.join(root, 'site', 'index.html'), 'w', encoding='utf-8').write(html)
print('site/index.html written;', len(out), 'release(s)')
