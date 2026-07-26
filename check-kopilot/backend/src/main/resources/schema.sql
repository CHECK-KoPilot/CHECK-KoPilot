CREATE TABLE IF NOT EXISTS stock_master (
    code    VARCHAR(12)  NOT NULL,
    name    VARCHAR(80)  NOT NULL,
    market  VARCHAR(10)  NOT NULL,          -- KOSPI | KOSDAQ (지수도 소속 시장을 적는다 — 호출 엔드포인트가 갈린다)
    type    VARCHAR(10)  NOT NULL,          -- STOCK | ETF | INDEX
    PRIMARY KEY (code),
    KEY idx_stock_master_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- CHECK API 장애 대비 영속 폴백 스냅샷 (단기 캐시는 Redis가 담당)
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
