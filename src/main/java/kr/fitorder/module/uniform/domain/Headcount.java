package kr.fitorder.module.uniform.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;

/** 정원현황 한 칸: 기준일자·부서·직군·직급별 정원. 원본의 넓은 표를 세로로 편 것. */
@Entity
@Table(name = "headcount")
public class Headcount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "load_id", nullable = false)
    private long loadId;

    @Column(name = "base_date", nullable = false)
    private LocalDate baseDate;

    @Column(nullable = false)
    private String department;

    @Column(name = "job_family", nullable = false)
    private String jobFamily;

    @Column(nullable = false)
    private String grade;

    @Column(nullable = false)
    private int quota;

    protected Headcount() {
    }

    public Headcount(long loadId, LocalDate baseDate, String department, String jobFamily, String grade, int quota) {
        this.loadId = loadId;
        this.baseDate = baseDate;
        this.department = department;
        this.jobFamily = jobFamily;
        this.grade = grade;
        this.quota = quota;
    }

    public LocalDate getBaseDate() {
        return baseDate;
    }

    public String getDepartment() {
        return department;
    }

    public String getJobFamily() {
        return jobFamily;
    }

    public String getGrade() {
        return grade;
    }

    public int getQuota() {
        return quota;
    }
}
