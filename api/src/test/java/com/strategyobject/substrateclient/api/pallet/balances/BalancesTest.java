package com.strategyobject.substrateclient.api.pallet.balances;

import com.strategyobject.substrateclient.api.Api;
import com.strategyobject.substrateclient.api.BalanceTransfer;
import com.strategyobject.substrateclient.api.TestsHelper;
import com.strategyobject.substrateclient.api.pallet.system.EventRecord;
//import com.strategyobject.substrateclient.api.pallet.system.System;
import com.strategyobject.substrateclient.common.convert.HexConverter;
import com.strategyobject.substrateclient.common.types.Size;
import com.strategyobject.substrateclient.crypto.Hasher;
import com.strategyobject.substrateclient.crypto.KeyPair;
import com.strategyobject.substrateclient.crypto.KeyRing;
import com.strategyobject.substrateclient.pallet.storage.Arg;
import com.strategyobject.substrateclient.rpc.api.*;
import com.strategyobject.substrateclient.rpc.api.primitives.BlockHash;
import com.strategyobject.substrateclient.rpc.api.primitives.BlockNumber;
import com.strategyobject.substrateclient.rpc.api.primitives.Index;
import com.strategyobject.substrateclient.rpc.api.section.Author;
import com.strategyobject.substrateclient.rpc.api.section.Chain;
import com.strategyobject.substrateclient.rpc.api.section.System;
import com.strategyobject.substrateclient.rpc.api.ExtrinsicStatus;
import com.strategyobject.substrateclient.scale.ScaleUtils;
import com.strategyobject.substrateclient.scale.registries.ScaleWriterRegistry;
import com.strategyobject.substrateclient.tests.containers.SubstrateVersion;
import com.strategyobject.substrateclient.tests.containers.TestSubstrateContainer;
import com.strategyobject.substrateclient.transport.ws.WsProvider;
import lombok.val;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.iterableWithSize;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
class BalancesTest {
    private static final int WAIT_TIMEOUT = 30;

    @Container
    private static final TestSubstrateContainer substrate = new TestSubstrateContainer(SubstrateVersion.V3_0_0).waitingFor(Wait.forLogMessage(".*Running JSON-RPC WS server.*", 1));


    private static final BigInteger TIP = BigInteger.valueOf(0);

    private static WsProvider.Builder wsProvider;
    private static Api api;
    private static Chain chain;
    private static System system;
    private static Author author;

    @BeforeAll
    static void getApiAndPallets() {
        wsProvider = WsProvider.builder().setEndpoint(substrate.getWsAddress());
        api = Api.with(wsProvider).build().join();
        system = api.rpc(System.class);
        chain = api.rpc(Chain.class);
        author = api.rpc(Author.class);
    }



    @Test
    void transfer() throws Exception {
        val wsProvider = WsProvider.builder().setEndpoint(substrate.getWsAddress());

        try (val api = Api.with(wsProvider).build().join()) {
            AtomicReference<List<EventRecord>> eventRecords = new AtomicReference<>(new ArrayList<>());
            val unsubscribe = api.pallet(com.strategyobject.substrateclient.api.pallet.system.System.class).events()
                    .subscribe((exception, block, value, keys) -> {
                        if (exception != null) {
                            throw new RuntimeException(exception);
                        }

                        eventRecords.set(value);
                    }, Arg.EMPTY)
                    .join();

            //doTransfer(api);
            doNewTransfer();

            await()
                    .atMost(WAIT_TIMEOUT, TimeUnit.SECONDS)
                    .untilAtomic(eventRecords, iterableWithSize(greaterThan(0)));

            assertTrue(unsubscribe.get().join());
            Supplier<Stream<Object>> events = () -> eventRecords.get().stream().map(x -> x.getEvent().getEvent());
            assertTrue(events.get().anyMatch(x -> x instanceof Balances.Transfer));
            assertTrue(events.get().anyMatch(x -> x instanceof com.strategyobject.substrateclient.api.pallet.system.System.ExtrinsicSuccess));
        }
    }

    @Test
    void newTransfer() throws Exception {
        AtomicReference<List<ExtrinsicStatus>> extrinsicStatusReference = new AtomicReference<>(new ArrayList<>());

        val aliceKeyRing = KeyRing.fromKeyPair(aliceKeyPair());
        val bobKeyRing = KeyRing.fromKeyPair(bobKeyPair());

        val createExtrinsic = createTransferExtrinsic(aliceKeyRing, bobKeyRing, BigInteger.valueOf(10));

        createExtrinsic.thenCompose( x -> author.submitAndWatchExtrinsic(x, (exception, extrinsicStatus) -> {
            if (exception != null)
                java.lang.System.out.println(exception);
            else
                extrinsicStatusReference.get().add(extrinsicStatus);
        })).join();

        await()
                .atMost(WAIT_TIMEOUT, TimeUnit.SECONDS)
                .untilAtomic(extrinsicStatusReference, iterableWithSize(greaterThan(0)));

        java.lang.System.out.println(extrinsicStatusReference);
    }


    private void doTransfer(Api api) {
        val genesis = api.rpc(Chain.class).getBlockHash(BlockNumber.GENESIS).join();
        assertDoesNotThrow(() ->
                api.rpc(Author.class).submitExtrinsic(createBalanceTransferExtrinsic(genesis)).join());
    }
    
    private void doNewTransfer() {
        val aliceKeyRing = KeyRing.fromKeyPair(aliceKeyPair());
        val bobKeyRing = KeyRing.fromKeyPair(bobKeyPair());
        val createExtrinsic = createTransferExtrinsic(aliceKeyRing, bobKeyRing, BigInteger.valueOf(10)).join();
        assertDoesNotThrow(() -> api.rpc(Author.class).submitExtrinsic(createExtrinsic).join());
    }

    private Extrinsic<?, ?, ?, ?> createBalanceTransferExtrinsic(BlockHash genesis) {
        val specVersion = 1;
        val txVersion = 1;
        val moduleIndex = (byte) 10;
        val callIndex = (byte) 2;
        val tip = 0;
        val call = new BalanceTransfer(moduleIndex, callIndex, AddressId.fromBytes(bobKeyPair().asPublicKey().getBytes()), BigInteger.valueOf(10));

        val extra = new SignedExtra<>(specVersion, txVersion, genesis, genesis, new ImmortalEra(), Index.of(0), BigInteger.valueOf(tip));
        val signedPayload = ScaleUtils.toBytes(
                new SignedPayload<>(call, extra),
                TestsHelper.SCALE_WRITER_REGISTRY,
                SignedPayload.class);
        val keyRing = KeyRing.fromKeyPair(aliceKeyPair());

        val signature = sign(keyRing, signedPayload);

        return Extrinsic.createSigned(
                new SignaturePayload<>(
                        AddressId.fromBytes(aliceKeyPair().asPublicKey().getBytes()),
                        signature,
                        extra
                ), call);
    }

    public CompletableFuture<Extrinsic<Call, Address, Signature, SignedExtra<ImmortalEra>>> createTransferExtrinsic(KeyRing fromKeyRing, KeyRing toKeyRing, BigInteger transferAmount) {
        byte MODULE_INDEX = 10; // authorship pallets from: https://github.com/LibertyDSNP/frequency/blob/main/runtime/frequency/src/lib.rs#L594-L641
        byte CALL_INDEX = 2; // // Call index obtained from state_getMetaData.
        long SPEC_VERSION = 1; // Spec version from running state_getRuntimeVersion
        long TX_VERSION = 1; // Tx version from running state_getRuntimeVersion

        val signerAddressId = AddressId.fromBytes(fromKeyRing.getPublicKey().getBytes());
        val signerAccountId = signerAddressId.getAddress();

        val destinationAddress = AddressId.fromBytes(toKeyRing.getPublicKey().getBytes());

        val call = new BalanceTransfer(MODULE_INDEX, CALL_INDEX, destinationAddress, transferAmount);

        return getGenesis().thenCombineAsync(getNonce(signerAccountId),
                (genesis, nonce) -> {

                    val extra = new SignedExtra<>(SPEC_VERSION, TX_VERSION, genesis, genesis, new ImmortalEra(), nonce, TIP);
                    //val writter = (ScaleWriter<? super SignedPayload<? super CreateSponsoredAccountWithDelegation, ? super SignedExtra<ImmortalEra>>>) registry.resolve(SignedPayload.class);
                    val signedPayload = ScaleUtils.toBytes(new SignedPayload<>(call, extra), TestsHelper.SCALE_WRITER_REGISTRY, SignedPayload.class);

                    val signature = sign(fromKeyRing, signedPayload);

                    return Extrinsic.createSigned(
                            new SignaturePayload<>(
                                    signerAddressId,
                                    signature,
                                    extra
                            ), call);
                });
    }

    private CompletableFuture<BlockHash> getGenesis() {
        return chain.getBlockHash(BlockNumber.GENESIS);
    }

    private CompletableFuture<Index> getNonce(AccountId accountId) {
        return system.accountNextIndex(accountId);
    }

    private Signature sign(KeyRing keyRing, byte[] payload) {
        val signature = payload.length > 256 ? Hasher.blake2(Size.of256, payload) : payload;

        return Sr25519Signature.from(keyRing.sign(() -> signature));
    }

    private KeyPair aliceKeyPair() {
        val str = "0x98319d4ff8a9508c4bb0cf0b5a78d760a0b2082c02775e6e82370816fedfff48925a225d97aa00682d6a59b95b18780c10d" +
                "7032336e88f3442b42361f4a66011d43593c715fdd31c61141abd04a99fd6822c8558854ccde39a5684e7a56da27d";

        return KeyPair.fromBytes(HexConverter.toBytes(str));
    }

    private KeyPair bobKeyPair() {
        val str = "0x081ff694633e255136bdb456c20a5fc8fed21f8b964c11bb17ff534ce80ebd5941ae88f85d0c1bfc37be41c904e1dfc01de" +
                "8c8067b0d6d5df25dd1ac0894a3258eaf04151687736326c9fea17e25fc5287613693c912909cb226aa4794f26a48";

        return KeyPair.fromBytes(HexConverter.toBytes(str));
    }
}