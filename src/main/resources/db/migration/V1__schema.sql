-- Миграция схемы для etu-reports-backend
-- Таблицы teaching_load_csv и students — внешние (1С/другая система).
-- Создаём только таблицы приложения, если их ещё нет.

CREATE TABLE IF NOT EXISTS public.student_reports (
    id SERIAL PRIMARY KEY,
    plan_row_id INT NOT NULL,
    student_id INT NOT NULL,
    group_name VARCHAR(255) NOT NULL,
    teacher_last_name VARCHAR(255) NOT NULL,
    topic VARCHAR(500),
    control_type VARCHAR(100),
    work_type VARCHAR(100),
    work_title VARCHAR(500),
    academic_year VARCHAR(20),
    upload_date DATE DEFAULT CURRENT_DATE,
    version INT NOT NULL DEFAULT 1,
    check_percent INT,
    status VARCHAR(100) DEFAULT 'Загружен',
    uploaded_by VARCHAR(255),
    file_name VARCHAR(500),
    file_path VARCHAR(1000)
);

CREATE INDEX IF NOT EXISTS idx_student_reports_plan_teacher
    ON public.student_reports (plan_row_id, teacher_last_name);
CREATE INDEX IF NOT EXISTS idx_student_reports_upload_date
    ON public.student_reports (upload_date DESC);

CREATE TABLE IF NOT EXISTS public.control_schedule (
    id SERIAL PRIMARY KEY,
    plan_row_id INT NOT NULL,
    week_start INT NOT NULL,
    week_end INT NOT NULL,
    control_text VARCHAR(500),
    topics TEXT
);

CREATE INDEX IF NOT EXISTS idx_control_schedule_plan_row
    ON public.control_schedule (plan_row_id);
