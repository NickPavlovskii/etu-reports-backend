package com.etu.reports.dto.student;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentDto {
    @JsonProperty("id")
    private Integer id;
    @JsonProperty("lastName")
    private String lastName;
    @JsonProperty("firstName")
    private String firstName;
    @JsonProperty("patronymic")
    private String patronymic;
    @JsonProperty("fio")
    private String fio;
    @JsonProperty("recordBook")
    private String recordBook;
    @JsonProperty("groupName")
    private String groupName;
}
