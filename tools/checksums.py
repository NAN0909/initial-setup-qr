#!/usr/bin/env python3
"""APK の QR 用チェックサムを出力する。

- package_checksum  : APK ファイル全体の SHA-256（URL-safe Base64, パディング無し）
                      → android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_CHECKSUM
- signature_checksum: 署名証明書(DER)の SHA-256（URL-safe Base64, パディング無し）
                      → android.app.extra.PROVISIONING_DEVICE_ADMIN_SIGNATURE_CHECKSUM
"""
import base64, hashlib, json, re, subprocess, sys, zipfile

apk = sys.argv[1]
data = open(apk, 'rb').read()
sha = hashlib.sha256(data).digest()
pkg_b64 = base64.urlsafe_b64encode(sha).decode().rstrip('=')

sig_b64 = None
try:
    out = subprocess.run(['apksigner', 'verify', '--print-certs', apk], capture_output=True, text=True).stdout
    m = re.search(r'certificate SHA-256 digest: ([0-9a-f]{64})', out)
    if m:
        sig_b64 = base64.urlsafe_b64encode(bytes.fromhex(m.group(1))).decode().rstrip('=')
except FileNotFoundError:
    pass

with zipfile.ZipFile(apk) as z:
    names = z.namelist()

print(json.dumps({
    'file': apk.split('/')[-1],
    'size': len(data),
    'sha256_hex': sha.hex(),
    'package_checksum': pkg_b64,
    'signature_checksum': sig_b64,
    'has_classes_dex': 'classes.dex' in names,
}, ensure_ascii=False, indent=2))
