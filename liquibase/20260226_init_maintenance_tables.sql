-- 保全案件主表
CREATE TABLE IF NOT EXISTS maintenance_case (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    policy_id VARCHAR(36) NOT NULL,
    customer_id VARCHAR(36) NOT NULL,
    maintenance_type VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL,
    effective_time_type VARCHAR(20) NOT NULL, -- IMMEDIATE, SPECIFIC_DATE, NEXT_PAYMENT_DATE
    specific_effective_date DATETIME,
    total_amount DECIMAL(15,2) NOT NULL DEFAULT 0,
    refund_amount DECIMAL(15,2) NOT NULL DEFAULT 0,
    description VARCHAR(500),
    created_at DATETIME NOT NULL,
    created_by VARCHAR(50) NOT NULL,
    updated_at DATETIME NOT NULL,
    updated_by VARCHAR(50) NOT NULL,
    tenant_id VARCHAR(36) NOT NULL,
    INDEX idx_maintenance_case_policy_id (policy_id),
    INDEX idx_maintenance_case_status (status),
    INDEX idx_maintenance_case_tenant_id (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 保全变更记录表
CREATE TABLE IF NOT EXISTS maintenance_change_record (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    maintenance_case_id VARCHAR(36) NOT NULL,
    change_type VARCHAR(50) NOT NULL, -- POLICY_INFO, INSURED_INFO, SUBJECT_INFO, COVERAGE_INFO, ETC.
    field_name VARCHAR(100) NOT NULL,
    old_value TEXT,
    new_value TEXT,
    created_at DATETIME NOT NULL,
    tenant_id VARCHAR(36) NOT NULL,
    INDEX idx_maintenance_change_record_case_id (maintenance_case_id),
    INDEX idx_maintenance_change_record_tenant_id (tenant_id),
    FOREIGN KEY (maintenance_case_id) REFERENCES maintenance_case(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 保全生效时间表
CREATE TABLE IF NOT EXISTS maintenance_effective_time (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    maintenance_case_id VARCHAR(36) NOT NULL,
    effective_time DATETIME NOT NULL,
    status VARCHAR(20) NOT NULL, -- PENDING, EFFECTIVE, EXPIRED
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    tenant_id VARCHAR(36) NOT NULL,
    INDEX idx_maintenance_effective_time_case_id (maintenance_case_id),
    INDEX idx_maintenance_effective_time_status (status),
    INDEX idx_maintenance_effective_time_tenant_id (tenant_id),
    FOREIGN KEY (maintenance_case_id) REFERENCES maintenance_case(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 保全项互斥关系表
CREATE TABLE IF NOT EXISTS maintenance_exclusion (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    maintenance_type_1 VARCHAR(50) NOT NULL,
    maintenance_type_2 VARCHAR(50) NOT NULL,
    description VARCHAR(200),
    tenant_id VARCHAR(36) NOT NULL,
    UNIQUE KEY uk_maintenance_exclusion (maintenance_type_1, maintenance_type_2, tenant_id),
    INDEX idx_maintenance_exclusion_tenant_id (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;