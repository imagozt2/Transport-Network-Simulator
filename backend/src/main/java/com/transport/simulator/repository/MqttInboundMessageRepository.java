package com.transport.simulator.repository;

import com.transport.simulator.entity.MqttInboundMessage;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MqttInboundMessageRepository extends JpaRepository<MqttInboundMessage, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select message from MqttInboundMessage message join fetch message.device where message.messageId = :messageId")
    Optional<MqttInboundMessage> findByMessageIdForUpdate(@Param("messageId") String messageId);
}
