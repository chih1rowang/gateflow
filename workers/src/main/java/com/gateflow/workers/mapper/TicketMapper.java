package com.gateflow.workers.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gateflow.workers.entity.Ticket;

/** MyBatis-Plus：继承 BaseMapper 即获得 insert/select 等基础 CRUD，无需写 SQL */
public interface TicketMapper extends BaseMapper<Ticket> {
}
