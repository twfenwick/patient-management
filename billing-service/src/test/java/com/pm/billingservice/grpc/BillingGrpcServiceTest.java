package com.pm.billingservice.grpc;

import billing.BillingRequest;
import billing.BillingResponse;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@ExtendWith(MockitoExtension.class)
class BillingGrpcServiceTest {

    @Mock
    private BillingRequest billingRequest;

    @Mock
    private StreamObserver<BillingResponse> responseObserver;

    @InjectMocks
    private BillingGrpcService billingGrpcService;


    @BeforeEach
    void setUp() {
        this.billingRequest = BillingRequest.newBuilder()
                .setPatientId("patient-123")
                .setName("name")
                .setEmail("email")
                .build();

    }

    @AfterEach
    void tearDown() {
    }

    @Test
    void createBillingAccount() {
        this.billingGrpcService.createBillingAccount(billingRequest, responseObserver);
        assertNotNull(responseObserver);

    }
}
