package com.titanium.maintenance.query.view;

import com.titanium.common.jpa.BaseView;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** 独立案件 before/proposed/applied 大快照引用投影。 */
@Entity
@Table(name = "t_maintenance_snapshot_view")
@Getter
@Setter
public class MaintenanceSnapshotView extends BaseView {

    @Id
    @Column(name = "maintenance_id", nullable = false, length = 64)
    private String maintenanceId;

    @Column(name = "before_storage_key", length = 512)
    private String beforeStorageKey;

    @Column(name = "before_content_hash", length = 64)
    private String beforeContentHash;

    @Column(name = "before_policy_version")
    private Long beforePolicyVersion;

    @Column(name = "before_captured_at", length = 40)
    private String beforeCapturedAt;

    @Column(name = "proposed_storage_key", length = 512)
    private String proposedStorageKey;

    @Column(name = "proposed_content_hash", length = 64)
    private String proposedContentHash;

    @Column(name = "proposed_policy_version")
    private Long proposedPolicyVersion;

    @Column(name = "proposed_captured_at", length = 40)
    private String proposedCapturedAt;

    @Column(name = "applied_storage_key", length = 512)
    private String appliedStorageKey;

    @Column(name = "applied_content_hash", length = 64)
    private String appliedContentHash;

    @Column(name = "applied_policy_version")
    private Long appliedPolicyVersion;

    @Column(name = "applied_captured_at", length = 40)
    private String appliedCapturedAt;
}
