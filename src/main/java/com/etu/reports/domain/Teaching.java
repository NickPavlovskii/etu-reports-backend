package com.etu.reports.domain;

public class Teaching {

    private String disciplineName;
    private String groupName;

    private String lastName;
    private String firstName;
    private String patronymic;

    private String departmentName;

    private String position;
    private String rank;
    private String degree;

    // --- getters/setters ---

    public String getDisciplineName() { return disciplineName; }
    public void setDisciplineName(String disciplineName) { this.disciplineName = disciplineName; }

    public String getGroupName() { return groupName; }
    public void setGroupName(String groupName) { this.groupName = groupName; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getPatronymic() { return patronymic; }
    public void setPatronymic(String patronymic) { this.patronymic = patronymic; }

    public String getDepartmentName() { return departmentName; }
    public void setDepartmentName(String v) { this.departmentName = v; }

    public String getPosition() { return position; }
    public void setPosition(String v) { this.position = v; }

    public String getRank() { return rank; }
    public void setRank(String v) { this.rank = v; }

    public String getDegree() { return degree; }
    public void setDegree(String v) { this.degree = v; }
}
