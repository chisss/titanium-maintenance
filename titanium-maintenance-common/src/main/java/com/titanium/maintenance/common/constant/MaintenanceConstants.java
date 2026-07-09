package com.titanium.maintenance.common.constant;

public class MaintenanceConstants {

    private MaintenanceConstants() {
        // 私有构造方法，防止实例化
    }

    // 保全状态常量
    public static final String MAINTENANCE_STATUS_PENDING              = "PENDING";
    public static final String MAINTENANCE_STATUS_PROCESSING           = "PROCESSING";
    public static final String MAINTENANCE_STATUS_APPROVED             = "APPROVED";
    public static final String MAINTENANCE_STATUS_REJECTED             = "REJECTED";
    public static final String MAINTENANCE_STATUS_COMPLETED            = "COMPLETED";

    // 保全类型常量
    public static final String MAINTENANCE_TYPE_POLICY_HOLDER_CHANGE   = "POLICY_HOLDER_CHANGE";
    public static final String MAINTENANCE_TYPE_BENEFICIARY_CHANGE     = "BENEFICIARY_CHANGE";
    public static final String MAINTENANCE_TYPE_PAYMENT_METHOD_CHANGE  = "PAYMENT_METHOD_CHANGE";
    public static final String MAINTENANCE_TYPE_ADDITIONAL_PAYMENT     = "ADDITIONAL_PAYMENT";
    public static final String MAINTENANCE_TYPE_REDUCTION_PAYMENT      = "REDUCTION_PAYMENT";
    public static final String MAINTENANCE_TYPE_POLICY_SUSPENSION      = "POLICY_SUSPENSION";
    public static final String MAINTENANCE_TYPE_POLICY_RESUMPTION      = "POLICY_RESUMPTION";
    public static final String MAINTENANCE_TYPE_POLICY_TERMINATION     = "POLICY_TERMINATION";

    // 新增保全类型常量
    public static final String MAINTENANCE_TYPE_POLICY_INFO_CHANGE     = "POLICY_INFO_CHANGE";
    public static final String MAINTENANCE_TYPE_POLICY_PERIOD_CHANGE   = "POLICY_PERIOD_CHANGE";
    public static final String MAINTENANCE_TYPE_COVERAGE_AMOUNT_CHANGE = "COVERAGE_AMOUNT_CHANGE";
    public static final String MAINTENANCE_TYPE_INSURED_INFO_CHANGE    = "INSURED_INFO_CHANGE";
    public static final String MAINTENANCE_TYPE_POLICY_REINSTATEMENT   = "POLICY_REINSTATEMENT";
    public static final String MAINTENANCE_TYPE_SUBJECT_CHANGE         = "SUBJECT_CHANGE";
    public static final String MAINTENANCE_TYPE_SMOKING_STATUS_CHANGE  = "SMOKING_STATUS_CHANGE";
    public static final String MAINTENANCE_TYPE_COVERAGE_CHANGE        = "COVERAGE_CHANGE";

    // 投连/万能形态专属保全类型常量（阶段四）
    public static final String MAINTENANCE_TYPE_FUND_SWITCH            = "FUND_SWITCH";
    public static final String MAINTENANCE_TYPE_TOP_UP                 = "TOP_UP";
    public static final String MAINTENANCE_TYPE_PARTIAL_WITHDRAWAL     = "PARTIAL_WITHDRAWAL";

    // 错误消息常量
    public static final String MAINTENANCE_NOT_FOUND                   = "保全记录不存在";
    public static final String INVALID_MAINTENANCE_STATUS              = "无效的保全状态";
    public static final String INVALID_MAINTENANCE_TYPE                = "无效的保全类型";
    public static final String MAINTENANCE_ALREADY_PROCESSED           = "保全记录已处理";
    public static final String CUSTOMER_NOT_FOUND                      = "客户不存在";
    public static final String POLICY_NOT_FOUND                        = "保单不存在";
    public static final String POLICY_NOT_ACTIVE                       = "保单未生效";
    public static final String MAINTENANCE_OUT_OF_COVERAGE             = "保全不在保险范围内";
    public static final String POLICY_NOT_TERMINATED                   = "保单未失效，无法复效";
    public static final String PENDING_MAINTENANCE_EXISTS              = "存在在途保全案件";
    public static final String MAINTENANCE_TYPE_EXCLUDED               = "该保全项与其他在途保全项互斥";

    // Kafka主题常量
    public static class KafkaTopic {
        public static final String MAINTENANCE_CREATED            = "maintenance-created";
        public static final String MAINTENANCE_UPDATED            = "maintenance-updated";
        public static final String MAINTENANCE_STATUS_CHANGED     = "maintenance-status-changed";
        public static final String MAINTENANCE_CHANGE_ADDED       = "maintenance-change-added";
        public static final String MAINTENANCE_PREMIUM_CALCULATED = "maintenance-premium-calculated";
        public static final String MAINTENANCE_EXECUTED           = "maintenance-executed";
        public static final String POLICY_UPDATED                 = "policy-updated";
        public static final String CUSTOMER_UPDATED               = "customer-updated";
    }
}
