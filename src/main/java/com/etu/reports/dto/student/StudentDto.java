package com.etu.reports.dto.student;

import com.fasterxml.jackson.annotation.JsonProperty;

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

    public StudentDto() {
    }

    public static Builder builder() {
        return new Builder();
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getPatronymic() {
        return patronymic;
    }

    public void setPatronymic(String patronymic) {
        this.patronymic = patronymic;
    }

    public String getFio() {
        return fio;
    }

    public void setFio(String fio) {
        this.fio = fio;
    }

    public String getRecordBook() {
        return recordBook;
    }

    public void setRecordBook(String recordBook) {
        this.recordBook = recordBook;
    }

    public String getGroupName() {
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    public static final class Builder {
        private Integer id;
        private String lastName;
        private String firstName;
        private String patronymic;
        private String fio;
        private String recordBook;
        private String groupName;

        public Builder id(Integer id) {
            this.id = id;
            return this;
        }

        public Builder lastName(String lastName) {
            this.lastName = lastName;
            return this;
        }

        public Builder firstName(String firstName) {
            this.firstName = firstName;
            return this;
        }

        public Builder patronymic(String patronymic) {
            this.patronymic = patronymic;
            return this;
        }

        public Builder fio(String fio) {
            this.fio = fio;
            return this;
        }

        public Builder recordBook(String recordBook) {
            this.recordBook = recordBook;
            return this;
        }

        public Builder groupName(String groupName) {
            this.groupName = groupName;
            return this;
        }

        public StudentDto build() {
            StudentDto o = new StudentDto();
            o.setId(id);
            o.setLastName(lastName);
            o.setFirstName(firstName);
            o.setPatronymic(patronymic);
            o.setFio(fio);
            o.setRecordBook(recordBook);
            o.setGroupName(groupName);
            return o;
        }
    }
}
