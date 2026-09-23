package com.smartlife.server.service;

import com.smartlife.common.context.BaseContext;
import com.smartlife.common.exception.BusinessException;
import com.smartlife.common.model.LoginUser;
import com.smartlife.pojo.dto.ShoppingCartDTO;
import com.smartlife.pojo.entity.ShoppingCart;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 购物车：原子自增合并、停售/售罄拒绝、减购到删除。DB 改动由事务回滚。
 */
@SpringBootTest
@Transactional
@DisplayName("购物车：纯 MySQL 原子操作")
class ShoppingCartServiceImplTest {

    private static final Long USER_ID = 48L;
    /** 蛋炒饭(id=3, stock=100, 在售)；薯条(id=11, 停售)；豆浆(id=10, stock=0) */
    private static final Long RICE = 3L;
    private static final Long STOPPED = 11L;
    private static final Long SOLD_OUT = 10L;

    @Autowired
    private IShoppingCartService cartService;

    @AfterEach
    void cleanUp() {
        BaseContext.remove();
    }

    private void login() {
        BaseContext.set(new LoginUser(USER_ID, 1, "测试用户"));
    }

    @Test
    @DisplayName("同一商品加两次只占一行，数量原子自增到 2")
    void addTwiceMergesToOneRow() {
        login();
        ShoppingCartDTO dto = new ShoppingCartDTO();
        dto.setDishId(RICE);
        cartService.add(dto);
        cartService.add(dto);

        List<ShoppingCart> carts = cartService.listMine();
        assertEquals(1, carts.size());
        assertEquals(2, carts.get(0).getNumber());
    }

    @Test
    @DisplayName("停售与售罄商品拒绝加购")
    void rejectsStoppedAndSoldOut() {
        login();
        ShoppingCartDTO stopped = new ShoppingCartDTO();
        stopped.setDishId(STOPPED);
        assertThrows(BusinessException.class, () -> cartService.add(stopped));

        ShoppingCartDTO soldOut = new ShoppingCartDTO();
        soldOut.setDishId(SOLD_OUT);
        assertThrows(BusinessException.class, () -> cartService.add(soldOut));
    }

    @Test
    @DisplayName("减购：2→1 保留，1→0 删行")
    void subDeletesRowWhenReachesZero() {
        login();
        ShoppingCartDTO dto = new ShoppingCartDTO();
        dto.setDishId(RICE);
        cartService.add(dto);
        cartService.add(dto);

        cartService.sub(dto);
        assertEquals(1, cartService.listMine().get(0).getNumber());

        cartService.sub(dto);
        assertTrue(cartService.listMine().isEmpty());
    }
}
