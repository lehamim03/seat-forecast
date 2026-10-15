# ==========================================================
#  수집한 bus_seats.csv 를 운행일별 파일(data/bus_seats_YYYY-MM-DD.csv)로 나눠 이어 붙이기
#  실행:  python save_data.py      (GitHub Actions 가 수집 뒤에 실행)
#  자정 이후 ~ 03:00 기록은 전날 운행일 파일에 붙입니다 (막차 운행분).
# ==========================================================
import csv
import os
from datetime import datetime, timedelta

from config import CSV_FILE, SERVICE_DAY_SHIFT_H


def main():
    if not os.path.exists(CSV_FILE):
        print("저장할 데이터 없음")
        return
    with open(CSV_FILE, encoding="utf-8-sig", newline="") as f:
        reader = csv.DictReader(f)
        columns, rows = reader.fieldnames, list(reader)
    by_day = {}
    for r in rows:
        t = datetime.strptime(r["collected_at"], "%Y-%m-%d %H:%M:%S")
        day = (t - timedelta(hours=SERVICE_DAY_SHIFT_H)).date().isoformat()
        by_day.setdefault(day, []).append(r)
    os.makedirs("data", exist_ok=True)
    for day, part in sorted(by_day.items()):
        path = f"data/bus_seats_{day}.csv"
        new = not os.path.exists(path)
        with open(path, "a", encoding="utf-8-sig" if new else "utf-8", newline="") as f:
            w = csv.DictWriter(f, fieldnames=columns)
            if new:
                w.writeheader()
            w.writerows(part)
        print(f"{path}: {len(part)}줄 {'새로 저장' if new else '이어 붙임'}")
    os.remove(CSV_FILE)


if __name__ == "__main__":
    main()
