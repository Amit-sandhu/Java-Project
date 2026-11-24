package matchbook.persistence;

import matchbook.model.Trade;
import java.io.IOException;
import java.util.Collection;
import java.util.List;

/** Storage contract for completed trades. */
public interface TradeRepository {
    void saveAll(Collection<Trade> trades) throws IOException;
    void appendAll(Collection<Trade> trades) throws IOException;
    List<Trade> findAll() throws IOException;
}
