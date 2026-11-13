package com.moonligh.benefit.infrastructure.persistence;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.moonligh.benefit.domain.BenefitLine;
import com.moonligh.benefit.domain.Bucket;
import com.moonligh.benefit.domain.Hold;
import com.moonligh.benefit.domain.Issued;
import com.moonligh.benefit.domain.NewCard;
import com.moonligh.benefit.domain.ProductHead;
import com.moonligh.benefit.domain.ProductItem;

@Mapper
public interface BenefitMapper {

    void ensureProduct();

    void ensureProductItem();

    void ensureCard();

    void ensureBalance();

    void ensureHold();

    Integer countProduct(@Param("code") String code);

    void upsertProduct(@Param("code") String code, @Param("name") String name, @Param("validDays") int validDays);

    void deleteItems(@Param("code") String code);

    void insertItem(@Param("code") String code, @Param("itemCode") String itemCode, @Param("quantity") BigDecimal quantity);

    ProductHead findProduct(@Param("code") String code);

    List<ProductItem> findItems(@Param("code") String code);

    Issued findBySource(@Param("channel") String channel, @Param("sourceRef") String sourceRef);

    void insertCard(NewCard card);

    void insertBalance(@Param("cardId") String cardId, @Param("itemCode") String itemCode, @Param("remaining") BigDecimal remaining);

    Hold findHold(@Param("orderId") String orderId);

    void insertHold(Hold hold);

    void replaceHold(Hold hold);

    void deleteHold(@Param("orderId") String orderId);

    void updateStatus(@Param("orderId") String orderId, @Param("status") int status);

    List<Bucket> lockAccount(@Param("accountId") String accountId, @Param("now") Instant now);

    int take(@Param("cardId") String cardId, @Param("itemCode") String itemCode, @Param("amount") BigDecimal amount);

    void giveBack(@Param("cardId") String cardId, @Param("itemCode") String itemCode, @Param("amount") BigDecimal amount);

    List<BenefitLine> lines(@Param("accountId") String accountId, @Param("now") Instant now);
}
