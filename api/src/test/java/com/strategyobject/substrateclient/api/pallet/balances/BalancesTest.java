package com.strategyobject.substrateclient.api.pallet.balances;

import com.strategyobject.substrateclient.api.Api;
import com.strategyobject.substrateclient.api.BalanceTransfer;
import com.strategyobject.substrateclient.api.TestsHelper;
import com.strategyobject.substrateclient.api.pallet.system.EventRecord;
import com.strategyobject.substrateclient.api.pallet.system.System;
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
import com.strategyobject.substrateclient.scale.ScaleUtils;
import com.strategyobject.substrateclient.scale.ScaleWriter;
import com.strategyobject.substrateclient.tests.containers.SubstrateVersion;
import com.strategyobject.substrateclient.tests.containers.TestSubstrateContainer;
import com.strategyobject.substrateclient.transport.ws.WsProvider;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
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
    private final TestSubstrateContainer substrate = new TestSubstrateContainer(SubstrateVersion.V3_0_0).waitingFor(Wait.forLogMessage(".*Running JSON-RPC WS server.*", 1));

    @Test
    void transfer() throws Exception {
        val wsProvider = WsProvider.builder().setEndpoint(substrate.getWsAddress());

        try (val api = Api.with(wsProvider).build().join()) {
            AtomicReference<List<EventRecord>> eventRecords = new AtomicReference<>(new ArrayList<>());
            val unsubscribe = api.pallet(System.class).events()
                    .subscribe((exception, block, value, keys) -> {
                        if (exception != null) {
                            throw new RuntimeException(exception);
                        }

                        eventRecords.set(value);
                    }, Arg.EMPTY)
                    .join();

            doTransfer(api);

            await()
                    .atMost(WAIT_TIMEOUT, TimeUnit.SECONDS)
                    .untilAtomic(eventRecords, iterableWithSize(greaterThan(1)));

            assertTrue(unsubscribe.get().join());
            Supplier<Stream<Object>> events = () -> eventRecords.get().stream().map(x -> x.getEvent().getEvent());
            assertTrue(events.get().anyMatch(x -> x instanceof Balances.Transfer));
            assertTrue(events.get().anyMatch(x -> x instanceof System.ExtrinsicSuccess));
        }
    }

    private void doTransfer(Api api) {
        val genesis = api.rpc(Chain.class).getBlockHash(BlockNumber.GENESIS).join();
        assertDoesNotThrow(() ->
                api.rpc(Author.class).submitExtrinsic(createBalanceTransferExtrinsic(genesis)).join());
    }

    @SuppressWarnings({"unchecked"})
    private Extrinsic<?, ?, ?, ?> createBalanceTransferExtrinsic(BlockHash genesis) {
        val specVersion = 264;
        val txVersion = 2;
        val moduleIndex = (byte) 6;
        val callIndex = (byte) 0;
        val tip = 0;
        val call = new BalanceTransfer(moduleIndex, callIndex, AddressId.fromBytes(bobKeyPair().asPublicKey().getBytes()), BigInteger.valueOf(10));

        val extra = new SignedExtra<>(specVersion, txVersion, genesis, genesis, new ImmortalEra(), Index.of(0), BigInteger.valueOf(tip));
        val writer = (ScaleWriter<? super SignedPayload<? super BalanceTransfer, ? super SignedExtra<ImmortalEra>>>) TestsHelper.SCALE_WRITER_REGISTRY.resolve(SignedPayload.class);
        val signedPayload = ScaleUtils.toBytes(new SignedPayload<>(call, extra), writer);
        val keyRing = KeyRing.fromKeyPair(aliceKeyPair());

        val signature = sign(keyRing, signedPayload);

        return Extrinsic.createSigned(
                new SignaturePayload<>(
                        AddressId.fromBytes(aliceKeyPair().asPublicKey().getBytes()),
                        signature,
                        extra
                ), call);
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