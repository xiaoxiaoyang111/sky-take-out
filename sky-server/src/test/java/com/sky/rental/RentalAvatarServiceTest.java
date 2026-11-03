package com.sky.rental;
import com.sky.exception.BaseException;
import com.sky.rental.auth.RentalContext;
import com.sky.rental.mapper.RentalCustomerMapper;
import com.sky.rental.model.RentalCustomer;
import com.sky.rental.service.*;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockMultipartFile;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class RentalAvatarServiceTest {
    RentalCustomerMapper customers;RentalImageService images;RentalAvatarService service;
    MockMultipartFile file=new MockMultipartFile("file","photo.png","image/png",new byte[]{1});
    @BeforeEach void init(){customers=mock(RentalCustomerMapper.class);images=mock(RentalImageService.class);service=new RentalAvatarService(customers,images);RentalContext.set(7L,"USER","name","user");}
    @AfterEach void clear(){RentalContext.clear();}
    @Test void avatarIsAssignedOnlyToAuthenticatedCustomer(){when(customers.findById(7L)).thenReturn(new RentalCustomer());when(images.upload(file)).thenReturn("/images/avatar.png");assertEquals("/images/avatar.png",service.upload(file).get("avatarUrl"));verify(customers).saveAvatar(7L,"/images/avatar.png");}
    @Test void missingCustomerCannotCreateUpload(){assertThrows(BaseException.class,()->service.upload(file));verifyNoInteractions(images);}
    @Test void guestAndEmployeeCannotUpload(){RentalContext.clear();assertThrows(BaseException.class,()->service.upload(file));RentalContext.set(1L,"ADMIN","staff","staff");assertThrows(BaseException.class,()->service.upload(file));verifyNoInteractions(images,customers);}
    @Test void invalidImageDoesNotChangeExistingAvatar(){when(customers.findById(7L)).thenReturn(new RentalCustomer());when(images.upload(file)).thenThrow(new BaseException("invalid image"));assertThrows(BaseException.class,()->service.upload(file));verify(customers,never()).saveAvatar(anyLong(),anyString());}
    @Test void persistenceFailureRemovesNewFileAndDoesNotLeakDatabaseError(){when(customers.findById(7L)).thenReturn(new RentalCustomer());when(images.upload(file)).thenReturn("/images/new.png");doThrow(new RuntimeException("sensitive diagnostic")).when(customers).saveAvatar(7L,"/images/new.png");String message=assertThrows(BaseException.class,()->service.upload(file)).getMessage();assertFalse(message.contains("sensitive"));verify(images).removeUploaded("/images/new.png");}
}
