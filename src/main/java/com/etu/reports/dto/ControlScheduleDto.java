package com.etu.reports.dto;

import java.util.List;

public record ControlScheduleDto(
        String groupName,
        int weekStart,
        int weekEnd,
        String controlText,
        List<String> topics
) {}
