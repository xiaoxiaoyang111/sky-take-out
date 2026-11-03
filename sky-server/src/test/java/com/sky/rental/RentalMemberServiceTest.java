package com.sky.rental;
import com.sky.exception.BaseException;
import com.sky.rental.auth.RentalContext;
import com.sky.rental.mapper.*;
import com.sky.rental.model.*;
import com.sky.rental.model.RentalMemberModels.*;
import com.sky.rental.service.*;
import org.junit.jupiter.api.*;
import org.springframework.dao.DuplicateKeyException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RentalMemberServiceTest {
    RentalMemberMapper mapper;RentalCustomerMapper customers;RentalCatalogService catalog;RentalMemberService service;
    @BeforeEach void init(){mapper=mock(RentalMemberMapper.class);customers=mock(RentalCustomerMapper.class);catalog=mock(RentalCatalogService.class);service=new RentalMemberService(mapper,customers,catalog);RentalContext.set(7L,"USER","old","user");}
    @AfterEach void clear(){RentalContext.clear();}
    ReviewRequest review(){ReviewRequest r=new ReviewRequest();r.setOrderId(11L);r.setRating(5);r.setContent("器材完好");return r;}
    RentalOrder order(String status,Long owner){RentalOrder o=new RentalOrder();o.setId(11L);o.setCustomerId(owner);o.setStatus(status);return o;}
    @Test void employeeCannotUsePersonalFeatures(){RentalContext.set(1L,"ADMIN","staff","staff");assertThrows(BaseException.class,service::summary);assertThrows(BaseException.class,()->service.favorite(1L,true));assertThrows(BaseException.class,()->service.review(review()));verifyNoInteractions(mapper,customers,catalog);}
    @Test void blankNicknameRejected(){ProfileRequest r=new ProfileRequest();r.setName(" ");assertThrows(BaseException.class,()->service.updateProfile(r));verifyNoInteractions(customers);}
    @Test void nameUpdateOnlyUsesAuthenticatedIdentity(){RentalCustomer c=new RentalCustomer();c.setUsername("user");when(customers.findById(7L)).thenReturn(c);ProfileRequest r=new ProfileRequest();r.setName(" 新昵称 ");assertEquals("新昵称",service.updateProfile(r).get("name"));verify(customers).updateName(7L,"新昵称");}
    @Test void favoriteChecksPublicEquipmentAndDeletionIsOwnerScoped(){service.favorite(10L,true);verify(catalog).equipment(10L,false);verify(mapper).addFavorite(7L,10L);service.favorite(10L,false);verify(mapper).removeFavorite(7L,10L);}
    @Test void paginationAndIdsAreBounded(){assertThrows(BaseException.class,()->service.favorites(0,12));assertThrows(BaseException.class,()->service.reviews(1,101));assertThrows(BaseException.class,()->service.favorite(-1L,true));verifyNoInteractions(mapper,catalog);}
    @Test void onlyCompletedOwnedOrderCanBeReviewed(){when(mapper.reviewOrder(11L)).thenReturn(order("RENTED",7L));assertThrows(BaseException.class,()->service.review(review()));when(mapper.reviewOrder(11L)).thenReturn(order("COMPLETED",8L));assertThrows(BaseException.class,()->service.review(review()));verify(mapper,never()).addReview(anyLong(),anyLong(),anyInt(),anyString());}
    @Test void duplicateReviewBecomesBusinessError(){when(mapper.reviewOrder(11L)).thenReturn(order("COMPLETED",7L));doThrow(new DuplicateKeyException("duplicate")).when(mapper).addReview(anyLong(),anyLong(),anyInt(),anyString());assertTrue(assertThrows(BaseException.class,()->service.review(review())).getMessage().contains("已评价"));}
    @Test void ratingAndContentValidatedBeforeDatabase(){ReviewRequest r=review();r.setRating(0);assertThrows(BaseException.class,()->service.review(r));r.setRating(5);r.setContent(" ");assertThrows(BaseException.class,()->service.review(r));verifyNoInteractions(mapper);}
}
