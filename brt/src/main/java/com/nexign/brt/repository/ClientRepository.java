package com.nexign.brt.repository;


import com.nexign.brt.model.Client;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;

@Repository
public interface ClientRepository extends JpaRepository<Client, Long> {

    Client findClientByClientNumber (Long clientNumber);

    @Modifying
    @Query("update Client c set c.balance = c.balance - ?1 where c.clientNumber = ?1")
    void setClientInfoById (Long phoneNumber, BigDecimal toPay);

}
