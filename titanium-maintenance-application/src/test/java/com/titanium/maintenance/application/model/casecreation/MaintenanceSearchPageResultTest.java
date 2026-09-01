package com.titanium.maintenance.application.model.casecreation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

class MaintenanceSearchPageResultTest {

    @Test
    void shouldExposeOneBasedPageAndTotalPages() {
        MaintenanceSearchPageResult result = MaintenanceSearchPageResult.of(List.of(), 21, 2, 10);

        assertEquals(21, result.total());
        assertEquals(2, result.pageNum());
        assertEquals(10, result.pageSize());
        assertEquals(3, result.totalPages());
    }
}
