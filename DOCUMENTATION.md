# ETU Reports Backend — Документация

## Обзор

Бэкенд для системы учёта учебных работ преподавателей. Позволяет:
- авторизацию по фамилии преподавателя (без пароля);
- просмотр дисциплин и групп;
- загрузку и скачивание работ студентов;
- просмотр архива отчётов.

---

## Схема базы данных

### Внешние таблицы (читаются, не управляются приложением)

**teaching_load_csv** — нагрузка преподавателей (возможно view):

| Колонка          | Тип    | Описание                    |
|------------------|--------|-----------------------------|
| plan_row_id      | INT    | ID строки плана             |
| discipline_name  | VARCHAR| Название дисциплины         |
| group_name       | VARCHAR| Группа                      |
| last_name        | VARCHAR| Фамилия преподавателя       |
| first_name       | VARCHAR| Имя                         |
| patronymic       | VARCHAR| Отчество                    |
| department_name  | VARCHAR| Кафедра                     |
| course           | INT    | Курс                        |
| semester         | INT    | Семестр                     |
| activity_type    | VARCHAR| Тип активности (Лекции, Пр.)|
| hours            | DECIMAL| Часы                        |
| has_exam         | BOOLEAN| Экзамен                     |
| has_pass         | BOOLEAN| Зачёт                       |
| has_pass_mark    | BOOLEAN| Зачёт с оценкой             |
| position         | VARCHAR| Должность                   |
| rank             | VARCHAR| Звание                      |
| degree           | VARCHAR| Степень                     |

**students** — студенты:

| Колонка      | Тип    | Описание              |
|--------------|--------|-----------------------|
| lk_id        | INT    | ID студента (из ЛК)   |
| group_name   | VARCHAR| Группа                |
| record_book  | VARCHAR| Номер зачётки         |
| status       | VARCHAR| Статус (не «Отчислен»)|
| academic_year| VARCHAR| Учебный год           |

### Таблицы приложения

**student_reports** — загруженные работы:

| Колонка          | Тип    | Описание                    |
|------------------|--------|-----------------------------|
| id               | SERIAL | PK                          |
| plan_row_id      | INT    | Ссылка на дисциплину        |
| student_id       | INT    | lk_id студента              |
| group_name       | VARCHAR| Группа                      |
| teacher_last_name| VARCHAR| Фамилия преподавателя       |
| topic            | VARCHAR| Тема (опционально)          |
| control_type     | VARCHAR| Тип контроля                |
| work_type        | VARCHAR| Тип работы                  |
| work_title       | VARCHAR| Название работы             |
| academic_year    | VARCHAR| Учебный год                 |
| upload_date      | DATE   | Дата загрузки               |
| version          | INT    | Версия (для пересдач)       |
| check_percent    | INT    | % проверки                  |
| status           | VARCHAR| Статус                      |
| uploaded_by      | VARCHAR| Кто загрузил                |
| file_name        | VARCHAR| Имя файла                   |
| file_path        | VARCHAR| Путь к файлу на диске       |

**control_schedule** — расписание контроля:

| Колонка      | Тип    | Описание                    |
|--------------|--------|-----------------------------|
| id           | SERIAL | PK                          |
| plan_row_id  | INT    | Ссылка на дисциплину        |
| week_start   | INT    | Неделя начала               |
| week_end     | INT    | Неделя окончания            |
| control_text | VARCHAR| Текст контроля              |
| topics       | TEXT   | Темы (JSON-массив)          |

### Связи

```
teaching_load_csv.plan_row_id ──┬── student_reports.plan_row_id
                                └── control_schedule.plan_row_id

students.lk_id ──────────────── student_reports.student_id
students.group_name ─────────── teaching_load_csv.group_name (по группам)
```

---

## Как работает бэкенд

### Стек

- **Spring Boot 3.5** — веб-фреймворк
- **Spring JDBC + HikariCP** — доступ к БД
- **PostgreSQL** — СУБД
- **Flyway** — миграции схемы
- **SpringDoc OpenAPI** — Swagger UI
- **Jackson** — JSON (встроен в Spring)

### Логика авторизации

- Авторизация выполняется **по фамилии преподавателя** (пароль не используется).
- Эндпоинт `GET /api/auth/login?lastName=Иванов` возвращает профиль преподавателя, если он есть в `teaching_load_csv` для кафедры из `app.department-filter`.
- Все эндпоинты с `{lastName}` в пути фильтруют данные по фамилии преподавателя.

### Фильтрация по кафедре

- Кафедра задаётся в `app.department-filter` (по умолчанию `каф.ИС`).
- Все запросы к `teaching_load_csv` учитывают `department_name = app.department-filter`.

### Хранение файлов

- Файлы сохраняются на диск в `app.reports-storage-dir` (по умолчанию `reports_storage`).
- В БД хранится путь к файлу (`file_path`).

---

## API Endpoints

Базовый URL: `http://localhost:8081`  
Swagger UI: `http://localhost:8081/swagger`

### Авторизация

| Метод | Путь | Описание |
|-------|------|----------|
| GET | `/api/auth/login?lastName={фамилия}` | Вход по фамилии. Ответ: `{ok, lastName, firstName, patronymic, fioShort, position, rank, degree, role, department}` |

### Преподаватель

| Метод | Путь | Описание |
|-------|------|----------|
| GET | `/api/teachers/{lastName}` | Нагрузка преподавателя (дисциплины, группы) |
| GET | `/api/teachers/{lastName}/disciplines` | Список уникальных дисциплин |

### Карточки дисциплин

| Метод | Путь | Описание |
|-------|------|----------|
| GET | `/api/teachers/{lastName}/disciplines/cards` | Карточки дисциплин (часы, группы, экзамен/зачёт) |
| GET | `/api/teachers/{lastName}/groups` | Список групп преподавателя |
| GET | `/api/teachers/{lastName}/discipline/{planRowId}/groups` | Группы по дисциплине |

### Страница дисциплины

| Метод | Путь | Описание |
|-------|------|----------|
| GET | `/api/teachers/{lastName}/discipline/{planRowId}` | Карточка дисциплины |
| GET | `/api/teachers/{lastName}/discipline/{planRowId}/students` | Студенты по дисциплине |
| GET | `/api/teachers/{lastName}/discipline/{planRowId}/reports` | Загруженные работы |
| GET | `/api/teachers/{lastName}/discipline/{planRowId}/controls` | Расписание контроля и темы |
| POST | `/api/teachers/{lastName}/discipline/{planRowId}/reports` | Загрузка работы (multipart/form-data) |

### Отчёты и скачивание

| Метод | Путь | Описание |
|-------|------|----------|
| GET | `/api/reports/{reportId}/download` | Скачать файл отчёта |

### Архив

| Метод | Путь | Описание |
|-------|------|----------|
| GET | `/api/teachers/{lastName}/archive/reports` | Архив отчётов преподавателя (только последние версии) |
| GET | `/api/archive/reports?teacherLastName={фамилия}` | Архив всех (опционально по преподавателю) |

---

## Параметры загрузки (POST reports)

`multipart/form-data`:

| Параметр | Тип | Обязательно | Описание |
|----------|-----|-------------|----------|
| file | File | да | Файл работы |
| studentId | int | да | lk_id студента |
| groupName | string | да | Группа |
| topic | string | нет | Тема |
| controlType | string | да | Тип контроля |
| workType | string | да | Тип работы |
| workTitle | string | да | Название работы |
| academicYear | string | да | Учебный год |
| autoCheck | boolean | да | Автопроверка |
| check | int | нет | % проверки |
| status | string | да | Статус (по умолчанию «Загружен») |
| uploadedBy | string | да | Кто загрузил |

---

## Конфигурация

### application.properties

| Свойство | По умолчанию | Описание |
|----------|--------------|----------|
| server.port | 8081 | Порт сервера |
| spring.datasource.url | jdbc:postgresql://localhost:5432/reports_db | URL БД |
| spring.datasource.username | niklad | Пользователь БД |
| spring.datasource.password | Niklad | Пароль БД |
| app.department-filter | каф.ИС | Кафедра для фильтрации |
| app.reports-storage-dir | reports_storage | Директория хранения файлов |
| app.cors-allowed-origins | http://localhost:5173 | CORS origins (через запятую) |
| spring.servlet.multipart.max-file-size | 50MB | Макс. размер файла |

### Переменные окружения

- `SPRING_DATASOURCE_USERNAME` — пользователь БД
- `SPRING_DATASOURCE_PASSWORD` — пароль БД

### Профили

- **dev** — подробные ошибки, SQL в логах: `--spring.profiles.active=dev`
- **prod** — без деталей ошибок: `--spring.profiles.active=prod`

---

## Запуск

```bash
./mvnw spring-boot:run
```

или

```bash
java -jar target/etu-reports-backend-0.0.1-SNAPSHOT.jar
```

Требования: JDK 17+, PostgreSQL с БД `reports_db` и таблицами `teaching_load_csv`, `students`, `student_reports`, `control_schedule`.
