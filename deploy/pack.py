# 서버로 올릴 묶음 만들기 — git 이 관리하는 파일 + 아직 커밋 안 한 새 파일 + 서버에 필요한 비밀 · 저장 파일
# 실행:  python deploy/pack.py <출력 tar.gz>      (deploy/upload.ps1 이 부름)
import os
import subprocess
import sys
import tarfile

EXTRA = ["api_key.py", "board_model.pkl", "odsay_cache.json", "transit_cache.json", "trip_odsay_cache.json",
         "gg_stations.json", "daytype_cache.json", "events_cache.json", "nearby_stops.json"]
SKIP_EXT = (".pptx",)   # 발표 자료는 서버에 필요 없음

root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
os.chdir(root)
out = sys.argv[1]
names = subprocess.run(["git", "-c", "core.quotepath=false", "ls-files", "-co", "--exclude-standard", "-z"],
                       capture_output=True, check=True).stdout.decode("utf-8").split("\0")
names = [n for n in names if n and os.path.isfile(n) and not n.endswith(SKIP_EXT)]
names += [n for n in EXTRA if os.path.isfile(n) and n not in names]
with tarfile.open(out, "w:gz") as tar:
    for n in names:
        tar.add(n, arcname=n.replace(os.sep, "/"))
print(f"files {len(names)}, {os.path.getsize(out) / 1e6:.1f} MB")
