package com.tourlk.repo;

import com.tourlk.entity.SupportTicket;
import com.tourlk.enums.TicketStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SupportTicketRepository extends JpaRepository<SupportTicket, Long> {

    List<SupportTicket> findByRaisedById(Long raisedById);

    List<SupportTicket> findByStatus(TicketStatus status);

    List<SupportTicket> findByAssignedToId(Long assignedToId);

    List<SupportTicket> findByAssignedToIsNull();

}
