CREATE TABLE IF NOT EXISTS stock_master (
    code    VARCHAR(12)  NOT NULL,
    name    VARCHAR(80)  NOT NULL,
    market  VARCHAR(10)  NOT NULL,          -- KOSPI | KOSDAQ (지수도 소속 시장을 적는다 — 호출 엔드포인트가 갈린다)
    type    VARCHAR(10)  NOT NULL,          -- STOCK | ETF | ETN | INDEX
    PRIMARY KEY (code),
    KEY idx_stock_master_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- CHECK API 장애 대비 영속 폴백 스냅샷 (단기 캐시는 Redis가 담당)
-- 구어체·약칭 → 종목코드. 공식 상장명("NAVER")과 사용자가 부르는 이름("네이버")이 다를 때를 메운다.
CREATE TABLE IF NOT EXISTS stock_alias (
    alias VARCHAR(80) NOT NULL,
    code  VARCHAR(12) NOT NULL,
    PRIMARY KEY (alias),
    KEY idx_stock_alias_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 되묻기 후보를 대표성 순으로 세우기 위한 가중치. 값이 클수록 앞에 온다.
-- 마스터 4천 행에는 시가총액 같은 대표성 신호가 없고 CHECK API에도 시총 상위 조회가 없어,
-- 큐레이션한 대표종목만 여기에 둔다(없는 종목은 0으로 취급).
CREATE TABLE IF NOT EXISTS stock_priority (
    code     VARCHAR(12) NOT NULL,
    priority INT         NOT NULL,
    PRIMARY KEY (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS check_fallback (
    cache_key  VARCHAR(200) NOT NULL,
    payload    LONGTEXT     NOT NULL,
    fetched_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (cache_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS card (
    id         CHAR(36)     NOT NULL,
    session_id VARCHAR(64)  NOT NULL,
    payload    LONGTEXT     NOT NULL,
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_card_session (session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS chat_log (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    session_id VARCHAR(64)  NOT NULL,
    role       VARCHAR(20)  NOT NULL,       -- user | assistant | tool_call | tool_result | error
    tool_name  VARCHAR(60)  NULL,
    content    LONGTEXT     NOT NULL,
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_chat_log_session (session_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 수요조사 적재 (가이드 카드 발생 = AUTO, 추가 요청 버튼 = EXPLICIT)
CREATE TABLE IF NOT EXISTS catalog_request (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    session_id      VARCHAR(64)  NULL,
    topic           VARCHAR(255) NOT NULL,  -- 집계 키 (LLM이 넘긴 topic 원문, 255자 절단)
    question        LONGTEXT     NULL,      -- 사용자의 원 질문(있으면)
    matched_api_ids VARCHAR(255) NULL,      -- 가이드가 제시한 CHECK API id들 (콤마 구분)
    source          VARCHAR(16)  NOT NULL DEFAULT 'AUTO',   -- AUTO | EXPLICIT
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_catalog_request_topic (topic),
    KEY idx_catalog_request_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 단축키 프리셋. prompt가 전송의 단일 진실이고, 나머지 컬럼은 폼 재편집용 메타다.
CREATE TABLE IF NOT EXISTS shortcut (
    id         CHAR(36)     NOT NULL,
    device_id  VARCHAR(64)  NOT NULL,
    key_combo  VARCHAR(40)  NOT NULL,   -- 정규화 문자열 "ctrl+shift+1"
    tool_name  VARCHAR(60)  NOT NULL,
    targets    VARCHAR(255) NOT NULL,   -- "삼성전자(005930),SK하이닉스(000660)"
    period     VARCHAR(20)  NULL,       -- 1M | 3M | 6M | 1Y (기간 없는 지표는 NULL)
    prompt     VARCHAR(300) NOT NULL,
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_shortcut_device_key (device_id, key_combo),
    KEY idx_shortcut_device (device_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
