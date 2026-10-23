package com.gateflow.workers;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 边界值测试：db_query 代码守卫的拒绝路径（不触库，纯守卫逻辑）。
 * 守卫只是第一层，真正的兜底是只读数据库账号 gateflow_ro（纵深防御）。
 */
class DbTasksGuardTest {

    private DbTasks newGuard() {
        // DriverManagerDataSource 构造时不建连接；拒绝路径不会走到查询
        return new DbTasks(null, "jdbc:mysql://localhost:3307/gateflow", "gateflow_ro", "gateflow_ro_pw");
    }

    @Test
    void nullSqlRejected() {
        assertThrows(IllegalArgumentException.class, () -> newGuard().query(null));
    }

    @Test
    void nonSelectRejected() {
        assertThrows(IllegalArgumentException.class, () -> newGuard().query("delete from expense_records"));
        assertThrows(IllegalArgumentException.class, () -> newGuard().query("update expense_records set amount=1"));
    }

    @Test
    void stackedStatementsRejected() {
        // 尾部分号可容忍，但语句内部不能再有分号（多语句拼接）
        assertThrows(IllegalArgumentException.class,
                () -> newGuard().query("select 1; drop table expense_records"));
        assertThrows(IllegalArgumentException.class,
                () -> newGuard().query("select 1; drop table expense_records;"));
    }

    @Test
    void intoOutfileRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> newGuard().query("select * from expense_records into outfile '/tmp/x'"));
        assertThrows(IllegalArgumentException.class,
                () -> newGuard().query("SELECT * FROM expense_records INTO DUMPFILE '/tmp/x'"));
    }

    @Test
    void lockingClausesRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> newGuard().query("select * from expense_records where id=1 for update"));
        assertThrows(IllegalArgumentException.class,
                () -> newGuard().query("select * from expense_records where id=1 lock in share mode"));
    }

    @Test
    void overlyLongStatementRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> newGuard().query("select '" + "a".repeat(2500) + "'"));
    }

    @Test
    void errorMessageCarriesHint() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> newGuard().query("truncate table expense_records"));
        assertTrue(e.getMessage().contains("SELECT"));
    }
}
