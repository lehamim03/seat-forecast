# ==========================================================
#  추천 순위 정하기 — 도착 예상이 빠른 순, 단 '사실상 같은' 선택지는 가까운 정류장 먼저
#
#  같은 노선은 앞 정류장일수록 버스가 일찍 오는 대신 오래 타서, 도착 예상이 거의 같게 나옵니다.
#  이때 몇십 초 차이(추정 오차 수준)로 더 걸어야 하는 정류장이 위로 올라가지 않도록,
#  같은 노선 · 탑승 확률 비슷 · 도착 예상 차이 TIE_MIN 분 이내면 도보가 짧은 쪽을 먼저 둡니다.
# ==========================================================
TIE_MIN = 2.0   # 도착 예상 차이가 이 안이면 같은 시간으로 봄 (분)
TIE_P = 0.10    # 탑승 확률 차이가 이 안이면 비슷하다고 봄


def rank(options, total, route, walk, prob):
    """options 를 추천 순서로 정렬해 새 목록으로 돌려줍니다.
    total·route·walk·prob: 선택지 → 도착까지 걸리는 분 · 노선 · 도보 분 · 탑승 확률"""
    rest = sorted(options, key=total)
    out = []
    while rest:
        best = rest[0]
        near = [o for o in rest
                if route(o) == route(best) and walk(o) < walk(best)
                and total(o) - total(best) <= TIE_MIN
                and abs(prob(o) - prob(best)) <= TIE_P]
        pick = min(near, key=lambda o: (walk(o), total(o))) if near else best
        out.append(pick)
        rest = [o for o in rest if o is not pick]
    return out
