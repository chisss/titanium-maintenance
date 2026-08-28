package com.titanium.maintenance.web.configuration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import com.titanium.maintenance.application.command.MaintenanceConfigurationCommandService;
import com.titanium.maintenance.application.model.configuration.MaintenanceConfigurationOperationContext;
import com.titanium.maintenance.application.query.MaintenanceConfigurationQueryService;
import com.titanium.maintenance.common.context.TenantContext;
import com.titanium.maintenance.exception.MaintenanceConfigurationPreconditionFailedException;
import com.titanium.maintenance.repository.MaintenanceItemConfigurationRepository.ConfigurationPage;
import com.titanium.maintenance.repository.MaintenanceItemConfigurationRepository.ConfigurationSearchCriteria;
import com.titanium.maintenance.web.controller.MaintenanceConfigurationController;
import com.titanium.maintenance.web.handler.MaintenanceExceptionHandler;
import com.titanium.maintenance.web.mapper.MaintenanceConfigurationWebMapper;
import com.titanium.maintenance.web.security.MaintenanceConfigurationRequestContextResolver;

@ExtendWith(MockitoExtension.class)
class MaintenanceConfigurationControllerTest {

    @Mock
    private MaintenanceConfigurationCommandService commandService;
    @Mock
    private MaintenanceConfigurationQueryService queryService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        MaintenanceConfigurationController controller = new MaintenanceConfigurationController(
                commandService, queryService, new MaintenanceConfigurationWebMapper(objectMapper),
                new MaintenanceConfigurationRequestContextResolver());
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new MaintenanceExceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    @Test
    void shouldReturnTenantScopedDetailWithEtagAndRedaction() throws Exception {
        authenticate("operator-1", "maintenance:config:view");
        TenantContext.setCurrentTenant("tenant-1");
        when(queryService.get("tenant-1", "config-1"))
                .thenReturn(MaintenanceConfigurationWebTestFixture.stored(7L));

        mockMvc.perform(get("/api/v1/maintenance/configurations/config-1"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, "\"7\""))
                .andExpect(jsonPath("$.sensitiveDetailsVisible").value(false))
                .andExpect(jsonPath("$.definition.fieldRules[0].detailsRedacted").value(true));

        verify(queryService).get("tenant-1", "config-1");
    }

    @Test
    void shouldMapStaleIfMatchToPreconditionFailed() throws Exception {
        authenticate("operator-1", "maintenance:config:retire");
        TenantContext.setCurrentTenant("tenant-1");
        when(commandService.retire(
                eq("config-1"), eq(6L), any(MaintenanceConfigurationOperationContext.class)))
                .thenThrow(new MaintenanceConfigurationPreconditionFailedException());

        mockMvc.perform(post("/api/v1/maintenance/configurations/config-1/retire")
                        .header(HttpHeaders.IF_MATCH, "\"6\""))
                .andExpect(status().isPreconditionFailed())
                .andExpect(jsonPath("$.code")
                        .value("MAINTENANCE_CONFIGURATION_PRECONDITION_FAILED"));
    }

    @Test
    void shouldRejectUnauthenticatedConfigurationRequest() throws Exception {
        TenantContext.setCurrentTenant("tenant-1");

        mockMvc.perform(get("/api/v1/maintenance/configurations/config-1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("MAINTENANCE_CONFIGURATION_UNAUTHENTICATED"));

        verify(queryService, never()).get(any(), any());
    }

    @Test
    void shouldRejectMissingIfMatchAsBadRequest() throws Exception {
        authenticate("operator-1", "maintenance:config:retire");
        TenantContext.setCurrentTenant("tenant-1");

        mockMvc.perform(post("/api/v1/maintenance/configurations/config-1/retire"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MAINTENANCE_INVALID_REQUEST"));

        verify(commandService, never()).retire(any(), any(Long.class), any());
    }

    @Test
    void shouldDeleteDraftWithCurrentEtag() throws Exception {
        authenticate("operator-1", "maintenance:config:edit");
        TenantContext.setCurrentTenant("tenant-1");

        mockMvc.perform(delete("/api/v1/maintenance/configurations/config-1")
                        .header(HttpHeaders.IF_MATCH, "\"4\""))
                .andExpect(status().isNoContent());

        verify(commandService).deleteDraft(
                eq("config-1"), eq(4L), any(MaintenanceConfigurationOperationContext.class));
    }

    @Test
    void shouldResolveEffectiveConfigurationByBusinessTime() throws Exception {
        authenticate("operator-1", "maintenance:config:view");
        TenantContext.setCurrentTenant("tenant-1");
        LocalDateTime businessTime = LocalDateTime.of(2026, 8, 25, 0, 0);
        when(queryService.resolveEffective("tenant-1", "CONTACT_CHANGE", businessTime))
                .thenReturn(MaintenanceConfigurationWebTestFixture.stored(8L));

        mockMvc.perform(get("/api/v1/maintenance/configurations/effective")
                        .param("itemCode", "CONTACT_CHANGE")
                        .param("businessTime", businessTime.toString()))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, "\"8\""));

        verify(queryService).resolveEffective("tenant-1", "CONTACT_CHANGE", businessTime);
    }

    @Test
    void shouldExposeWorkflowAndFeeSummaryInConfigurationList() throws Exception {
        authenticate("operator-1", "maintenance:config:view");
        TenantContext.setCurrentTenant("tenant-1");
        when(queryService.search(eq("tenant-1"), any(ConfigurationSearchCriteria.class)))
                .thenReturn(new ConfigurationPage(
                        List.of(MaintenanceConfigurationWebTestFixture.stored(7L)), 1, 0, 20));

        mockMvc.perform(get("/api/v1/maintenance/configurations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].stepCount").value(3))
                .andExpect(jsonPath("$.items[0].feeMode").value("NONE"));
    }

    private void authenticate(String operatorId, String... authorities) {
        var authentication = new UsernamePasswordAuthenticationToken(
                operatorId, "N/A", AuthorityUtils.createAuthorityList(authorities));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
