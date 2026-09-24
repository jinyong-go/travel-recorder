-- 로컬 전용 초기 데이터. 프론트엔드 목업(frontend/src/data)을 옮긴 것이다.
-- application-local.yml 에서만 읽는다. dev·prod 에는 적재하지 않는다.
-- 로컬 DB 는 인메모리 H2 라 기동마다 새로 만들어지므로 재실행을 고려하지 않는다.
--
-- id 는 목업과 같은 값을 쓴다. 목업끼리의 참조(여행 → 기록, 그룹 → 멤버)를 그대로 옮기기 위해서다.
-- 파일 끝에서 IDENTITY 시퀀스를 다시 시작시킨다.

-- 사용자
-- LocalLoginConfig 의 계정 시더는 SQL 초기화 뒤에 돈다. 여행·그룹이 참조할 행이 먼저 있어야 하므로
-- 여기서 넣고, 시더는 provider + provider_id 로 이 행을 찾아 이름·이메일을 덮어쓴다.
-- user4 는 로그인 계정이 없다. 여행 소유자·그룹 멤버로만 등장한다.
INSERT INTO users (id, provider, provider_id, email, name, created_at)
VALUES
    (1, 'local', 'user1', 'user1@example.com', '사용자1', TIMESTAMP WITH TIME ZONE '2026-08-01 12:00:00+09:00'),
    (2, 'local', 'user2', 'user2@example.com', '사용자2', TIMESTAMP WITH TIME ZONE '2026-08-01 12:00:00+09:00'),
    (3, 'local', 'user3', 'user3@example.com', '사용자3', TIMESTAMP WITH TIME ZONE '2026-08-01 12:00:00+09:00'),
    (4, 'local', 'user4', 'user4@example.com', '사용자4', TIMESTAMP WITH TIME ZONE '2026-08-01 12:00:00+09:00');

-- 공유 그룹. created_at 은 소유자의 참여일이다.
INSERT INTO share_group (id, owner_id, name, memo, created_at)
VALUES
    (1, 1, '가족', '설 연휴 사진 공유용', TIMESTAMP WITH TIME ZONE '2026-08-01 12:00:00+09:00'),
    (2, 2, '제주 동행', '2026 여름 제주 같이 간 사람들', TIMESTAMP WITH TIME ZONE '2026-08-10 12:00:00+09:00'),
    (3, 2, '회사 동료', NULL, TIMESTAMP WITH TIME ZONE '2026-09-01 12:00:00+09:00');

-- 그룹 멤버 (소유자 포함)
-- 목업에서는 여행 5(소유자 user3)가 그룹 2 에 공유되어 있지만 user3 이 멤버가 아니다.
-- 서버는 소유자가 속한 그룹에만 공유를 허용하므로 user3 을 그룹 2 멤버로 넣어 맞춘다.
INSERT INTO group_member (id, group_id, user_id, joined_at)
VALUES
    (1, 1, 1, TIMESTAMP WITH TIME ZONE '2026-08-01 12:00:00+09:00'),
    (2, 1, 3, TIMESTAMP WITH TIME ZONE '2026-08-03 12:00:00+09:00'),
    (3, 2, 2, TIMESTAMP WITH TIME ZONE '2026-08-10 12:00:00+09:00'),
    (4, 2, 1, TIMESTAMP WITH TIME ZONE '2026-08-12 12:00:00+09:00'),
    (5, 2, 4, TIMESTAMP WITH TIME ZONE '2026-08-15 12:00:00+09:00'),
    (6, 2, 3, TIMESTAMP WITH TIME ZONE '2026-08-20 12:00:00+09:00'),
    (7, 3, 2, TIMESTAMP WITH TIME ZONE '2026-09-01 12:00:00+09:00'),
    (8, 3, 4, TIMESTAMP WITH TIME ZONE '2026-09-02 12:00:00+09:00');

-- 대기 중인 초대. user1 로 로그인하면 받은 초대 배지가 바로 보이도록 넣는다.
INSERT INTO group_invite (id, group_id, invitee_id, invited_by, created_at)
VALUES
    (1, 3, 1, 2, TIMESTAMP WITH TIME ZONE '2026-09-10 12:00:00+09:00');

-- 여행
INSERT INTO trips (id, owner_id, name, start_date, end_date, headcount, budget, memo, visibility, created_at, updated_at)
VALUES
    (1, 1, '제주 3박 4일', DATE '2026-09-05', DATE '2026-09-08', 4, 1250000, '가족들과 다녀온 첫 제주. 흑돼지와 해변 위주로 돌았고, 마지막 날에는 면세점에 들러 선물을 샀다. 다음에는 우도까지 넘어가 보고 싶다.', 'GROUP', TIMESTAMP WITH TIME ZONE '2026-09-09 12:00:00+09:00', TIMESTAMP WITH TIME ZONE '2026-09-09 12:00:00+09:00'),
    (2, 1, '혼자 걷는 제주', DATE '2026-09-02', DATE '2026-09-04', 1, 420000, '아무에게도 알리지 않고 다녀온 혼행.', 'PRIVATE', TIMESTAMP WITH TIME ZONE '2026-09-04 12:00:00+09:00', TIMESTAMP WITH TIME ZONE '2026-09-04 12:00:00+09:00'),
    (3, 1, '제주 맛집 투어', DATE '2026-09-06', DATE '2026-09-06', 2, NULL, '당일치기로 유명한 집만 골라 다녔다.', 'PUBLIC', TIMESTAMP WITH TIME ZONE '2026-09-06 12:00:00+09:00', TIMESTAMP WITH TIME ZONE '2026-09-06 12:00:00+09:00'),
    (4, 2, '가을 제주 억새길', DATE '2026-08-31', DATE '2026-09-01', 3, 680000, '억새가 한창일 때 맞춰 다녀왔다.', 'GROUP', TIMESTAMP WITH TIME ZONE '2026-09-01 12:00:00+09:00', TIMESTAMP WITH TIME ZONE '2026-09-01 12:00:00+09:00'),
    (5, 3, '먹고 걷는 제주', DATE '2026-08-30', DATE '2026-08-30', 2, NULL, '근고기 하나 먹으러 간 당일치기.', 'GROUP', TIMESTAMP WITH TIME ZONE '2026-08-30 12:00:00+09:00', TIMESTAMP WITH TIME ZONE '2026-08-30 12:00:00+09:00'),
    (6, 2, '제주 동굴 탐방', DATE '2026-08-25', DATE '2026-08-29', 2, 900000, '동굴과 오름을 번갈아 다닌 일정. 만장굴은 여름에 가도 서늘하니 겉옷을 챙기는 편이 좋다.', 'PUBLIC', TIMESTAMP WITH TIME ZONE '2026-08-29 12:00:00+09:00', TIMESTAMP WITH TIME ZONE '2026-08-29 12:00:00+09:00'),
    (7, 3, '제주 소품샵 산책', DATE '2026-08-26', DATE '2026-08-29', 1, 350000, '해변 근처 소품샵만 골라 다녔다.', 'PUBLIC', TIMESTAMP WITH TIME ZONE '2026-08-29 12:00:00+09:00', TIMESTAMP WITH TIME ZONE '2026-08-29 12:00:00+09:00'),
    (8, 4, '제주 미식 여행', DATE '2026-08-27', DATE '2026-08-28', 4, 1100000, '흑우와 해산물 위주.', 'PUBLIC', TIMESTAMP WITH TIME ZONE '2026-08-28 12:00:00+09:00', TIMESTAMP WITH TIME ZONE '2026-08-28 12:00:00+09:00');

-- 여행 ↔ 그룹 공유 (GROUP 여행만)
INSERT INTO trip_shares (id, trip_id, group_id)
VALUES
    (1, 1, 1),
    (2, 1, 2),
    (3, 4, 2),
    (4, 5, 2);

-- 여행 기록. 주소·좌표는 장소 검색 목업(placeSearchResults.js)의 값이다.
INSERT INTO trip_records (id, trip_id, name, category, address, latitude, longitude, rating, memo, created_at, updated_at)
VALUES
    (1, 2, '제주 흑돼지 본가', 'FOOD', '제주특별자치도 제주시 흑돼지거리 12', 33.4996, 126.5312, 4.5, '숙소 근처 흑돼지 맛집, 웨이팅 있음', TIMESTAMP WITH TIME ZONE '2026-09-10 12:00:00+09:00', TIMESTAMP WITH TIME ZONE '2026-09-10 12:00:00+09:00'),
    (2, 1, '함덕 해수욕장 편집샵', 'SHOPPING', '제주특별자치도 제주시 조천읍 함덕로 45', 33.5434, 126.6692, 4, '감성 소품샵, 엽서 구매', TIMESTAMP WITH TIME ZONE '2026-09-09 12:00:00+09:00', TIMESTAMP WITH TIME ZONE '2026-09-09 12:00:00+09:00'),
    (3, 3, '성산일출봉', 'SIGHT', '제주특별자치도 서귀포시 성산읍 성산리 1', 33.4587, 126.9425, 5, '일출 명소, 아침 일찍 방문 추천', TIMESTAMP WITH TIME ZONE '2026-09-08 12:00:00+09:00', TIMESTAMP WITH TIME ZONE '2026-09-08 12:00:00+09:00'),
    (4, 1, '동문시장 야시장', 'FOOD', '제주특별자치도 제주시 관덕로14길 20', 33.5141, 126.5283, 4.5, '흑돼지 꼬치, 오메기떡', TIMESTAMP WITH TIME ZONE '2026-09-08 12:00:00+09:00', TIMESTAMP WITH TIME ZONE '2026-09-08 12:00:00+09:00'),
    (5, 2, '오설록 티뮤지엄', 'SIGHT', '제주특별자치도 서귀포시 안덕면 신화역사로 15', 33.3055, 126.2892, 4.5, '녹차 아이스크림, 티하우스', TIMESTAMP WITH TIME ZONE '2026-09-07 12:00:00+09:00', TIMESTAMP WITH TIME ZONE '2026-09-07 12:00:00+09:00'),
    (6, 2, '협재 해변 기념품샵', 'SHOPPING', '제주특별자치도 제주시 한림읍 협재로 105', 33.3939, 126.2397, 4, '조개 공예품 판매', TIMESTAMP WITH TIME ZONE '2026-09-07 12:00:00+09:00', TIMESTAMP WITH TIME ZONE '2026-09-07 12:00:00+09:00'),
    (7, 3, '우진해장국', 'FOOD', '제주특별자치도 제주시 서사로 11', 33.5121, 126.5219, 4.5, '몸국이 유명, 현지인 맛집', TIMESTAMP WITH TIME ZONE '2026-09-06 12:00:00+09:00', TIMESTAMP WITH TIME ZONE '2026-09-06 12:00:00+09:00'),
    (8, 1, '한라산 국립공원', 'SIGHT', '제주특별자치도 제주시 1100로 2070-61', 33.3792, 126.4975, 5, '어리목 코스, 반나절 소요', TIMESTAMP WITH TIME ZONE '2026-09-05 12:00:00+09:00', TIMESTAMP WITH TIME ZONE '2026-09-05 12:00:00+09:00'),
    (9, 2, '이호테우 해변', 'SIGHT', '제주특별자치도 제주시 이호일동 375-1', 33.5039, 126.4653, 4.5, '목마 등대, 노을 명소', TIMESTAMP WITH TIME ZONE '2026-09-05 12:00:00+09:00', TIMESTAMP WITH TIME ZONE '2026-09-05 12:00:00+09:00'),
    (10, 2, '제주시 로컬 편집샵', 'SHOPPING', '제주특별자치도 제주시 탑동로 6', 33.5097, 126.5219, 4, '제주 브랜드 굿즈', TIMESTAMP WITH TIME ZONE '2026-09-04 12:00:00+09:00', TIMESTAMP WITH TIME ZONE '2026-09-04 12:00:00+09:00'),
    (11, 3, '올레시장 고등어회', 'FOOD', '제주특별자치도 서귀포시 중앙로62번길 18', 33.2496, 126.5624, 4.5, '고등어회, 갈치조림', TIMESTAMP WITH TIME ZONE '2026-09-04 12:00:00+09:00', TIMESTAMP WITH TIME ZONE '2026-09-04 12:00:00+09:00'),
    (12, 2, '카멜리아힐', 'SIGHT', '제주특별자치도 서귀포시 안덕면 병악로 166', 33.2839, 126.3757, 4.5, '동백꽃 정원 산책', TIMESTAMP WITH TIME ZONE '2026-09-03 12:00:00+09:00', TIMESTAMP WITH TIME ZONE '2026-09-03 12:00:00+09:00'),
    (13, 1, '중문 면세점', 'SHOPPING', '제주특별자치도 서귀포시 중문관광로 72beon-gil 35', 33.2506, 126.4128, 4, '화장품, 특산품 쇼핑', TIMESTAMP WITH TIME ZONE '2026-09-02 12:00:00+09:00', TIMESTAMP WITH TIME ZONE '2026-09-02 12:00:00+09:00'),
    (14, 2, '흑돼지 명가 칠성점', 'FOOD', '제주특별자치도 제주시 칠성로길 22', 33.5133, 126.5253, 4.5, '2차로 방문, 육즙 최고', TIMESTAMP WITH TIME ZONE '2026-09-02 12:00:00+09:00', TIMESTAMP WITH TIME ZONE '2026-09-02 12:00:00+09:00'),
    (15, 4, '산굼부리', 'SIGHT', '제주특별자치도 제주시 조천읍 비자림로 768', 33.4459, 126.6601, 4.5, '억새 명소, 가을 추천', TIMESTAMP WITH TIME ZONE '2026-09-01 12:00:00+09:00', TIMESTAMP WITH TIME ZONE '2026-09-01 12:00:00+09:00'),
    (16, 4, '애월 카페거리', 'SIGHT', '제주특별자치도 제주시 애월읍 애월로 1', 33.4633, 126.3298, 5, '오션뷰 카페 다수', TIMESTAMP WITH TIME ZONE '2026-08-31 12:00:00+09:00', TIMESTAMP WITH TIME ZONE '2026-08-31 12:00:00+09:00'),
    (17, 5, '돈사돈', 'FOOD', '제주특별자치도 제주시 도령로 8', 33.4879, 126.489, 4.5, '근고기 맛집, 예약 필수', TIMESTAMP WITH TIME ZONE '2026-08-30 12:00:00+09:00', TIMESTAMP WITH TIME ZONE '2026-08-30 12:00:00+09:00'),
    (18, 6, '만장굴', 'SIGHT', '제주특별자치도 제주시 구좌읍 만장굴길 182', 33.5286, 126.7714, 4, '용암동굴, 서늘함 주의', TIMESTAMP WITH TIME ZONE '2026-08-29 12:00:00+09:00', TIMESTAMP WITH TIME ZONE '2026-08-29 12:00:00+09:00'),
    (19, 7, '세화 해변 소품샵', 'SHOPPING', '제주특별자치도 제주시 구좌읍 해맞이해안로 1447', 33.5261, 126.8583, 4, '핸드메이드 액세서리', TIMESTAMP WITH TIME ZONE '2026-08-29 12:00:00+09:00', TIMESTAMP WITH TIME ZONE '2026-08-29 12:00:00+09:00'),
    (20, 8, '제주 흑우 명가', 'FOOD', '제주특별자치도 서귀포시 태평로 401', 33.2541, 126.5601, 4.5, '흑우 스테이크', TIMESTAMP WITH TIME ZONE '2026-08-28 12:00:00+09:00', TIMESTAMP WITH TIME ZONE '2026-08-28 12:00:00+09:00'),
    (21, 8, '섭지코지', 'SIGHT', '제주특별자치도 서귀포시 성산읍 섭지코지로 107', 33.4238, 126.9275, 5, '유채꽃, 드라마 촬영지', TIMESTAMP WITH TIME ZONE '2026-08-27 12:00:00+09:00', TIMESTAMP WITH TIME ZONE '2026-08-27 12:00:00+09:00'),
    (22, 7, '표선 민속촌 기념품점', 'SHOPPING', '제주특별자치도 서귀포시 표선면 민속해안로 631', 33.3255, 126.833, 4, '전통 공예품', TIMESTAMP WITH TIME ZONE '2026-08-26 12:00:00+09:00', TIMESTAMP WITH TIME ZONE '2026-08-26 12:00:00+09:00'),
    (23, 6, '성산일출봉', 'SIGHT', '제주특별자치도 서귀포시 성산읍 성산리 1', 33.4587, 126.9425, 4.5, '흐려서 일출은 못 봤지만 경치는 좋았어요', TIMESTAMP WITH TIME ZONE '2026-08-25 12:00:00+09:00', TIMESTAMP WITH TIME ZONE '2026-08-25 12:00:00+09:00');

-- id 를 직접 넣으면 IDENTITY 시퀀스가 따라 올라가지 않는다. 앱이 새 행을 만들 때 1번부터
-- 발급해 충돌하므로, 초기 데이터가 쓴 범위 밖에서 다시 시작하게 한다.
ALTER TABLE users ALTER COLUMN id RESTART WITH 1000;
ALTER TABLE share_group ALTER COLUMN id RESTART WITH 1000;
ALTER TABLE group_member ALTER COLUMN id RESTART WITH 1000;
ALTER TABLE group_invite ALTER COLUMN id RESTART WITH 1000;
ALTER TABLE trips ALTER COLUMN id RESTART WITH 1000;
ALTER TABLE trip_shares ALTER COLUMN id RESTART WITH 1000;
ALTER TABLE trip_records ALTER COLUMN id RESTART WITH 1000;