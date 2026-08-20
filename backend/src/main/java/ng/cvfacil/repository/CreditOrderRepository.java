package ng.cvfacil.repository;

import java.util.Optional;
import java.util.UUID;
import ng.cvfacil.domain.CreditOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CreditOrderRepository extends JpaRepository<CreditOrder, UUID> {

  Optional<CreditOrder> findByPagSeguroOrderId(String pagSeguroOrderId);

  /**
   * Transição de status atômica e idempotente: só aplica se o status atual for exatamente {@code
   * expectedStatus}. Se o webhook do PagSeguro for reenviado (retry comum em gateways), a segunda
   * chamada encontra o status já alterado e retorna 0 — o chamador NÃO deve conceder crédito de
   * novo nesse caso.
   */
  @Modifying
  @Query(
      "UPDATE CreditOrder o SET o.status = :newStatus, o.updatedAt = CURRENT_TIMESTAMP "
          + "WHERE o.pagSeguroOrderId = :orderId AND o.status = :expectedStatus")
  int updateStatusIfCurrent(
      @Param("orderId") String orderId,
      @Param("expectedStatus") CreditOrder.Status expectedStatus,
      @Param("newStatus") CreditOrder.Status newStatus);
}
