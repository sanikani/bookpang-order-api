package shop.sajotuna.order.stock.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import shop.sajotuna.order.stock.domain.BookStock;

import java.util.List;
import java.util.Optional;

public interface BookStockRepository extends JpaRepository<BookStock, Long> {
    Optional<BookStock> findByIsbn(String isbn);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from BookStock b where b.isbn = :isbn")
    Optional<BookStock> findByIsbnWithPessimisticLock(@Param("isbn") String isbn);

    boolean existsByIsbn(String isbn);

    List<BookStock> findByIsbnIn(List<String> isbns);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            update book_stock
            set quantity = quantity - :quantity,
                version = version + 1
            where isbn = :isbn
              and quantity >= :quantity
            """, nativeQuery = true)
    int decreaseStockAtomically(@Param("isbn") String isbn, @Param("quantity") int quantity);
}
