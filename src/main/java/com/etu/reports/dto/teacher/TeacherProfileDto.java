package com.etu.reports.dto.teacher;

import com.fasterxml.jackson.annotation.JsonProperty;

public class TeacherProfileDto {
    private String lastName;
    private String firstName;
    private String patronymic;
    private String fio;
    private String email;
    private String role;
    private String position;
    private String rank;
    private String degree;
    private Integer disciplinesCount;

    @JsonProperty("lastName")
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    @JsonProperty("firstName")
    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    @JsonProperty("patronymic")
    public String getPatronymic() { return patronymic; }
    public void setPatronymic(String patronymic) { this.patronymic = patronymic; }

    @JsonProperty("fio")
    public String getFio() { return fio; }
    public void setFio(String fio) { this.fio = fio; }

    @JsonProperty("email")
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    @JsonProperty("role")
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    @JsonProperty("roleDisplay")
    public String getRoleDisplay() {
        if (role == null || role.isBlank()) return "Преподаватель";
        boolean admin = false;
        boolean head = false;
        boolean teacher = false;
        for (String token : role.split(",")) {
            String normalized = token == null ? "" : token.trim().toUpperCase();
            if ("ADMIN".equals(normalized)) admin = true;
            else if ("HEAD_DEPARTMENT".equals(normalized) || "HEAD".equals(normalized)) head = true;
            else if ("TEACHER".equals(normalized)) teacher = true;
        }
        StringBuilder out = new StringBuilder();
        if (admin) out.append("Администратор");
        if (head) {
            if (!out.isEmpty()) out.append(", ");
            out.append("Зав. кафедры");
        }
        if (teacher || out.isEmpty()) {
            if (!out.isEmpty()) out.append(", ");
            out.append("Преподаватель");
        }
        return out.toString();
    }

    @JsonProperty("position")
    public String getPosition() { return position; }
    public void setPosition(String position) { this.position = position; }

    @JsonProperty("rank")
    public String getRank() { return rank; }
    public void setRank(String rank) { this.rank = rank; }

    @JsonProperty("degree")
    public String getDegree() { return degree; }
    public void setDegree(String degree) { this.degree = degree; }

    @JsonProperty("disciplinesCount")
    public Integer getDisciplinesCount() { return disciplinesCount; }
    public void setDisciplinesCount(Integer disciplinesCount) { this.disciplinesCount = disciplinesCount; }
}
