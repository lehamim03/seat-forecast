/**
 * 좌석예보 설문조사 구글폼 자동 생성 스크립트
 *
 * 사용법
 *  1. https://script.google.com 접속 → [새 프로젝트]
 *  2. 기본 코드를 모두 지우고 이 파일 내용을 붙여넣기 → 저장(Ctrl+S)
 *  3. 위쪽 함수 선택 칸에서 createSurvey 선택 → [실행]
 *  4. 처음 한 번 권한 승인 창이 뜨면 본인 계정으로 [허용]
 *     ("확인되지 않은 앱" 경고 → [고급] → [(프로젝트 이름)(으)로 이동])
 *  5. 아래 [실행 로그]에 나오는 '응답용 링크'를 배포하면 끝
 *     (폼과 응답 시트는 내 구글 드라이브에 생성됩니다)
 */
function createSurvey() {
  const form = FormApp.create('광역버스 만석 경험 설문조사');
  form.setDescription(
    '안녕하세요. 수원 → 서울 광역버스의 "만석으로 못 타는 문제"를 해결하는 ' +
    '앱 서비스(가칭: 좌석예보)를 기획하고 있습니다.\n' +
    '응답은 익명으로 수집되며 수업 과제 목적으로만 사용됩니다. (약 2분 소요)'
  );
  form.setCollectEmail(false);
  form.setProgressBar(true);
  form.setConfirmationMessage('응답해 주셔서 감사합니다! 좋은 서비스로 보답하겠습니다.');

  // ---------- 대상자 확인 ----------
  const screen = form.addMultipleChoiceItem()
    .setTitle('경기도 ↔ 서울 광역버스(빨간 버스)를 이용하시나요?')
    .setRequired(true);
  form.addPageBreakItem().setTitle('광역버스 이용 경험');
  screen.setChoices([
    screen.createChoice('예', FormApp.PageNavigationType.CONTINUE),
    screen.createChoice('아니요', FormApp.PageNavigationType.SUBMIT), // 대상이 아니면 바로 제출
  ]);

  // Q1
  form.addMultipleChoiceItem()
    .setTitle('Q1. 광역버스를 얼마나 자주 이용하시나요?')
    .setChoiceValues(['주 1회 미만', '주 1~2회', '주 3~4회', '주 5회 이상'])
    .setRequired(true);

  // Q2
  form.addTextItem()
    .setTitle('Q2. 주로 이용하는 노선과 탑승 정류소를 적어주세요.')
    .setHelpText('예: 8800 / 삼성1차아파트')
    .setRequired(true);

  // Q3
  form.addMultipleChoiceItem()
    .setTitle('Q3. 최근 1개월 동안 만석이라 버스를 타지 못한 적이 몇 번 있나요?')
    .setChoiceValues(['없음', '1~2회', '3~5회', '6~10회', '11회 이상'])
    .setRequired(true);

  // Q4
  form.addCheckboxItem()
    .setTitle('Q4. 만석으로 버스를 못 탔을 때 주로 어떻게 하시나요? (복수 선택)')
    .setChoiceValues([
      '같은 노선 다음 버스를 기다린다',
      '다른 광역버스 노선을 탄다',
      '지하철·전철로 바꾼다',
      '택시 등 다른 수단을 이용한다',
      '애초에 일찍 나가서 줄을 선다',
    ])
    .showOtherOption(true)
    .setRequired(true);

  // Q5
  form.addCheckboxItem()
    .setTitle('Q5. 버스를 탈 때 사용하는 길찾기·버스 앱은 무엇인가요? (복수 선택)')
    .setChoiceValues(['네이버 지도', '카카오맵', '카카오버스', '경기버스정보(GBIS)', '사용하지 않음'])
    .showOtherOption(true)
    .setRequired(true);

  // Q6
  form.addScaleItem()
    .setTitle('Q6. 출발 전에 "이 버스를 탈 수 있을 확률"을 알려주는 앱이 있다면 사용하시겠어요?')
    .setBounds(1, 5)
    .setLabels('전혀 사용하지 않겠다', '꼭 사용하겠다')
    .setRequired(true);

  // Q7
  form.addCheckboxItem()
    .setTitle('Q7. 가장 원하는 기능을 골라주세요. (최대 3개)')
    .setChoiceValues([
      '탈 수 있는 확률을 반영한 경로 추천',
      '시간대별 좌석 예보 (몇 시 버스가 여유 있는지)',
      '집에서 나설 시각 알림',
      '실시간 버스 위치·잔여 좌석 확인',
      '즐겨찾기 경로 저장',
    ])
    .showOtherOption(true)
    .setValidation(FormApp.createCheckboxValidation().requireSelectAtMost(3).build())
    .setRequired(true);

  // ---------- 기타 ----------
  form.addPageBreakItem().setTitle('마지막 질문');
  form.addMultipleChoiceItem()
    .setTitle('현재 신분을 선택해주세요.')
    .setChoiceValues(['대학생', '직장인', '기타'])
    .setRequired(true);
  form.addParagraphTextItem()
    .setTitle('광역버스 이용 중 불편했던 점이나 바라는 점이 있다면 자유롭게 적어주세요. (선택)');

  // 응답을 구글 시트로 자동 저장 (그래프 만들기 편함)
  const sheet = SpreadsheetApp.create('광역버스 만석 경험 설문조사 (응답)');
  form.setDestination(FormApp.DestinationType.SPREADSHEET, sheet.getId());

  Logger.log('응답용 링크 (이걸 배포하세요): ' + form.getPublishedUrl());
  Logger.log('폼 편집 링크: ' + form.getEditUrl());
  Logger.log('응답 시트: ' + sheet.getUrl());
}
