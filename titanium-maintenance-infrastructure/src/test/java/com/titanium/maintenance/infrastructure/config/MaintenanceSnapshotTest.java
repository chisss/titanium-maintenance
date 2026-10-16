package com.titanium.maintenance.infrastructure.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.axonframework.commandhandling.SimpleCommandBus;
import org.axonframework.commandhandling.gateway.DefaultCommandGateway;
import org.axonframework.eventhandling.DomainEventMessage;
import org.axonframework.eventhandling.GenericDomainEventMessage;
import org.axonframework.eventsourcing.AggregateSnapshotter;
import org.axonframework.eventsourcing.EventCountSnapshotTriggerDefinition;
import org.axonframework.eventsourcing.EventSourcingRepository;
import org.axonframework.eventsourcing.GenericAggregateFactory;
import org.axonframework.eventsourcing.SnapshotTriggerDefinition;
import org.axonframework.eventsourcing.eventstore.DomainEventStream;
import org.axonframework.eventsourcing.eventstore.EmbeddedEventStore;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.eventsourcing.eventstore.inmemory.InMemoryEventStorageEngine;
import org.axonframework.messaging.GenericMessage;
import org.axonframework.messaging.unitofwork.DefaultUnitOfWork;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.axonframework.modelling.command.AggregateAnnotationCommandHandler;
import org.axonframework.modelling.command.Repository;
import org.axonframework.serialization.SerializedObject;
import org.axonframework.serialization.SimpleSerializedObject;
import org.axonframework.serialization.json.JacksonSerializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.titanium.maintenance.aggregate.Maintenance;
import com.titanium.maintenance.aggregate.MaintenanceWorkflow;
import com.titanium.maintenance.command.AddMaintenanceChangeCommand;
import com.titanium.maintenance.command.CreateMaintenanceCommand;
import com.titanium.maintenance.common.enums.EffectiveTimeType;
import com.titanium.maintenance.common.enums.MaintenanceChangeType;
import com.titanium.maintenance.valueobject.CustomerId;
import com.titanium.maintenance.valueobject.MaintenanceId;
import com.titanium.maintenance.valueobject.PolicyId;
import com.titanium.metadata.enums.maintenance.MaintenanceType;

/**
 * 保全聚合快照机制测试（m25-01 / DSN-01 止血：上帝聚合的事件流快照兜底）。
 * <p>
 * 覆盖四层判据：
 * <ol>
 * <li><b>接线</b>：AxonConfig 产出的 {@code Repository<Maintenance>} 必须挂载快照触发器，阈值独立断言为生产值 50（判据值不读被测常量，防「两侧同源同错」）；</li>
 * <li><b>机制</b>：真实命令流跨过阈值后，事件存储中必须出现快照（载荷为聚合状态实例），且加载之后存储中的快照内容仍保持在快照时刻；</li>
 * <li><b>消费对照</b>：带快照的加载实际消费的非快照事件数必须严格小于纯回放加载（计数尺子由纯回放侧自证：恰好等于事件总数）——若快照未被加载路径使用，本判据判红。快照识别判据 = payload 为聚合实例（Axon 4.10 消息接口无 isSnapshot 标记，普通事件 payload 恒为事件类）；</li>
 * <li><b>往返一致</b>：快照恢复状态、序列化再还原状态、纯回放状态三者的序列化视图必须逐字段一致，且字段数不低于下界（防「空壳聚合」假绿）。</li>
 * </ol>
 * 🔴 <b>存储语义口径（判据成立的前提，勿删）</b>：Axon 恢复聚合时，若事件流首条是快照，则<b>直接以快照载荷（聚合状态对象）作为聚合根</b>
 * （{@code AbstractAggregateFactory.fromSnapshot}）。生产 JPA 引擎每次读取都把存储字节反序列化为新对象，加载拿到的是副本；而 InMemory 引擎按对象引用存储，
 * 原样返回会让加载把存储中的同一对象直接当聚合根——后续命令在它之上叠加事件，下一次加载又把增量事件重复应用到它之上，读数逐轮复利膨胀。
 * 故此处包装事件存储：读取流中的聚合状态载荷用 {@link JacksonSerializer} 深复制（模拟生产的反序列化边界），其余消息原样透传并计数。
 * 变更时间等业务字段由事件载荷携带（命令处理时生成、随事件流冻结），故逐字段比较与墙钟无关、可稳定复现。
 * </p>
 */
class MaintenanceSnapshotTest {

    /** 生产快照阈值（独立重声明，不引用 AxonConfig 私有常量——判据与实现不共用同一来源） */
    private static final int SNAPSHOT_THRESHOLD_PRODUCTION = 50;

    private static final String TENANT_ID  = "tenant-snapshot-probe";
    private static final String OPERATOR   = "snapshot-probe";
    /** 1 个创建事件 + 59 个变更事件，确保跨过生产阈值 50 */
    private static final int    TOTAL_EVENTS = 60;
    private static final int    CHANGE_COUNT = TOTAL_EVENTS - 1;

    /** 阈值 50 在第 50 个事件（create + 49 条变更）提交时触发 ⇒ 快照应含 49 条变更（「快照时刻」基准） */
    private static final int EXPECTED_SNAPSHOT_CHANGES = 49;

    /**
     * 序列化视图字段数下界（防「空壳」假绿：若序列化器看不见私有字段，输出为空对象，比较必然「相等」）。
     * DSN-01c 批 4 下沉（2026-10-15）后实测 30（33 − 4 个顶层 effect 键转 @JsonIgnore 转发 + 1 个
     * effectPlan 成员键，与预测精确一致）；同批幂等账本修复再 +1（fieldConflictOperationHashes 由
     * 无 getter 转为进序列化）⇒ 实测 31。本值贴着实测取，任何属性静默丢失（含成员对象整体不进 JSON）
     * 都会跌破下界。
     * 🔴 DSN-01c 四个成员（retroactiveResolution / settlement / workflow / effectPlan）已全部下沉完毕、
     * 账本修复亦已收口，本值即为终态；后续任何字段增删（含无 getter 字段处置）须同批实测并调整本值后复跑本测试。
     */
    private static final int SERIALIZED_FIELD_FLOOR = 31;

    @Test
    @DisplayName("接线：AxonConfig 产出的仓库必须挂载快照触发器，生产阈值为 50")
    void shouldWireSnapshotTriggerIntoRepository() throws Exception {
        InMemoryEventStorageEngine engine = new InMemoryEventStorageEngine();
        EmbeddedEventStore eventStore = EmbeddedEventStore.builder().storageEngine(engine).build();
        try {
            AxonConfig config = new AxonConfig();
            SnapshotTriggerDefinition trigger = config.maintenanceSnapshotTriggerDefinition((type, id) -> {
            });
            Repository<Maintenance> repository = config.maintenanceRepository(eventStore, trigger);

            Field triggerField = EventSourcingRepository.class.getDeclaredField("snapshotTriggerDefinition");
            triggerField.setAccessible(true);
            assertSame(trigger, triggerField.get(repository), "仓库必须挂载快照触发器（显式 Repository Bean 装配路径）");

            Field thresholdField = EventCountSnapshotTriggerDefinition.class.getDeclaredField("threshold");
            thresholdField.setAccessible(true);
            assertEquals(SNAPSHOT_THRESHOLD_PRODUCTION, thresholdField.getInt(trigger),
                    "生产阈值须为 50（2026-10-15 实测：单聚合事件数最多 37、达到 50 的为 0）");
        } finally {
            eventStore.shutDown();
        }
    }

    @Test
    @DisplayName("机制+往返：跨阈值产生快照且被加载消费，快照/序列化还原/纯回放三者状态一致")
    void snapshotRoundTripShouldMatchEventReplay() throws Exception {
        InMemoryEventStorageEngine engineA = new InMemoryEventStorageEngine();
        EmbeddedEventStore storeA = EmbeddedEventStore.builder().storageEngine(engineA).build();
        InMemoryEventStorageEngine engineB = new InMemoryEventStorageEngine();
        EmbeddedEventStore storeB = EmbeddedEventStore.builder().storageEngine(engineB).build();
        try {
            JacksonSerializer serializer = JacksonSerializer.defaultSerializer();
            AtomicInteger consumedOnA = new AtomicInteger();
            AtomicInteger consumedOnB = new AtomicInteger();
            EventStore storeForA = deserializingCountingEventStore(storeA, consumedOnA, serializer);
            EventStore storeForB = deserializingCountingEventStore(storeB, consumedOnB, serializer);

            AggregateSnapshotter snapshotter = AggregateSnapshotter.builder()
                    .eventStore(storeForA)
                    .executor(Runnable::run)
                    .aggregateFactories(new GenericAggregateFactory<>(Maintenance.class))
                    .build();

            // A 路径：真实 AxonConfig 装配（生产阈值 50）
            AxonConfig config = new AxonConfig();
            Repository<Maintenance> repositoryA = config.maintenanceRepository(storeForA,
                    config.maintenanceSnapshotTriggerDefinition(snapshotter));
            // B 路径：无快照触发器的纯回放对照（事件从 A 复制，保证载荷逐条相同）
            Repository<Maintenance> repositoryB = EventSourcingRepository.builder(Maintenance.class)
                    .eventStore(storeForB)
                    .build();

            MaintenanceId id = runCommandFlow(repositoryA, CHANGE_COUNT);
            String aggregateId = id.id();

            // —— 判据 2：跨过阈值后快照确实产生。Axon 4.10 的消息接口无 isSnapshot 标记，
            // 快照识别判据 = payload 为聚合实例（普通事件的 payload 恒为事件类，二者无交集）——
            Optional<DomainEventMessage<?>> snapshot = engineA.readSnapshot(aggregateId);
            assertTrue(snapshot.isPresent(), "事件数(" + TOTAL_EVENTS + ")跨过阈值(" + SNAPSHOT_THRESHOLD_PRODUCTION + ")后必须产生快照");
            assertTrue(snapshot.get().getPayload() instanceof Maintenance, "快照载荷必须是聚合状态实例（而非事件类）");
            assertTrue(storeA.readEvents(aggregateId).peek().getPayload() instanceof Maintenance,
                    "事件流头部应是快照（加载路径据此跳过快照覆盖的事件段）");

            // —— 复制全量事件到对照引擎。🔴 必须用引擎层直读：EventStore 层的 readEvents 会把最新快照
            // 拼在流头（上方判据刚实证：快照 + 增量 == 10 条），引擎层 readEvents 才是纯事件（无快照拼接）——
            List<DomainEventMessage<?>> replayedEvents = new ArrayList<>();
            engineA.readEvents(aggregateId).asStream().forEach(replayedEvents::add);
            assertEquals(TOTAL_EVENTS, replayedEvents.size(), "对照引擎应恰好收到全部回放事件（不含快照）");
            engineB.appendEvents(replayedEvents);

            // —— 判据 3：消费计数对照（证明带快照的加载确实跳过了快照覆盖段）——
            consumedOnA.set(0);
            Maintenance stateA = loadState(repositoryA, aggregateId);
            consumedOnB.set(0);
            Maintenance stateB = loadState(repositoryB, aggregateId);
            assertEquals(TOTAL_EVENTS, consumedOnB.get(), "纯回放必须消费全部事件（计数尺子有效性自证）");
            assertTrue(consumedOnA.get() < consumedOnB.get(),
                    "带快照的加载须跳过被快照覆盖的事件段，实测 A=" + consumedOnA.get() + " B=" + consumedOnB.get());

            // —— 判据 2（续）：存储中的快照必须仍处于「快照时刻」形态 ——
            // 若加载路径拿到的是存储对象本身（InMemory 引用语义），命令会在它之上叠加变更，此处读数必然大于 49；
            // 该断言即「反序列化边界」包装的哨兵，也等价于生产 JPA 引擎「读取即反序列化为新对象」的行为。
            assertEquals(EXPECTED_SNAPSHOT_CHANGES,
                    ((Maintenance) engineA.readSnapshot(aggregateId).orElseThrow().getPayload()).getChanges().size(),
                    "加载/命令处理不允许原地改写存储中的快照载荷");

            // —— 判据 4：序列化往返与回放一致（含防「空壳」字段数下界）——
            ObjectMapper mapper = new ObjectMapper();
            SerializedObject<byte[]> serializedB = serializer.serialize(stateB, byte[].class);
            JsonNode treeB = mapper.readTree(serializedB.getData());
            JsonNode treeA = mapper.readTree(serializer.serialize(stateA, byte[].class).getData());
            Object restored = serializer.deserialize(serializedB);
            JsonNode treeRestored = mapper.readTree(serializer.serialize(restored, byte[].class).getData());

            assertTrue(treeB.size() >= SERIALIZED_FIELD_FLOOR,
                    "序列化视图必须覆盖聚合字段（防空壳假绿），实测字段数=" + treeB.size());
            // DSN-01c 成员下沉防护：成员对象必须整体进入序列化视图（root 侧成员字段无 getter 时静默丢失、不抛异常）
            assertTrue(treeB.path("settlement").size() > 0,
                    "settlement 成员实体必须整体进入序列化视图（DSN-01c 批 2）；缺失/空壳说明 root 侧成员字段未进 JSON");
            assertTrue(treeB.path("retroactiveResolution").size() > 0,
                    "retroactiveResolution 成员实体必须整体进入序列化视图（DSN-01c 批 1）；缺失/空壳说明 root 侧成员字段未进 JSON");
            assertTrue(treeB.path("workflow").size() > 0,
                    "workflow 成员实体必须整体进入序列化视图（DSN-01c 批 3）；缺失/空壳说明 root 侧成员字段未进 JSON");
            assertTrue(treeB.path("effectPlan").size() > 0,
                    "effectPlan 成员实体必须整体进入序列化视图（DSN-01c 批 4）；缺失/空壳说明 root 侧成员字段未进 JSON");
            // DSN-01c 批 4 账本修复防护：幂等账本必须进序列化视图（无 getter 时整键静默缺失、恢复后条目丢失；
            // 测试流中账本为空表，故断言键存在而非非空）
            assertTrue(treeB.has("fieldConflictOperationHashes"),
                    "聚合根冲突操作账本必须进序列化视图（DSN-01c 批 4 修复）；缺失说明 root 侧账本 getter 未生效");
            assertTrue(treeB.path("workflow").has("workflowOperationHashes"),
                    "工作流幂等账本必须进序列化视图（DSN-01c 批 4 修复）；缺失说明成员账本 getter 被抑制或未生成");
            assertEquals(treeB, treeA, "快照恢复状态与事件回放状态必须逐字段一致");
            assertEquals(treeB, treeRestored, "序列化再还原必须与事件回放结果一致");
        } finally {
            storeA.shutDown();
            storeB.shutDown();
        }
    }

    @Test
    @DisplayName("幂等账本跨序列化往返存活（批 4 账本修复：条目必须进快照，防同一操作重试绕过幂等）")
    void idempotencyLedgersShouldSurviveSnapshotRoundTrip() throws Exception {
        InMemoryEventStorageEngine engine = new InMemoryEventStorageEngine();
        EmbeddedEventStore store = EmbeddedEventStore.builder().storageEngine(engine).build();
        try {
            JacksonSerializer serializer = JacksonSerializer.defaultSerializer();
            Repository<Maintenance> repository = EventSourcingRepository.builder(Maintenance.class)
                    .eventStore(store)
                    .build();

            MaintenanceId id = runCommandFlow(repository, 0);
            Maintenance state = loadState(repository, id.id());
            // 账本条目在生产中仅由事件回放写入，命令流无法直接构造，此处反射注入以还原「已有操作记录的案件」
            setLedgerEntry(Maintenance.class, state, "fieldConflictOperationHashes", "op-root", "hash-root");
            setLedgerEntry(MaintenanceWorkflow.class, state.getWorkflow(), "workflowOperationHashes", "op-wf", "hash-wf");

            SerializedObject<byte[]> serialized = serializer.serialize(state, byte[].class);
            Maintenance restored = serializer.deserialize(serialized);

            assertEquals("hash-root", restored.getFieldConflictOperationHashes().get("op-root"),
                    "冲突操作账本条目必须经快照序列化往返存活（丢失 ⇒ 同一 operationId 重试绕过幂等、重复追加冲突事实）");
            assertEquals("hash-wf", restored.getWorkflow().getWorkflowOperationHashes().get("op-wf"),
                    "工作流账本条目必须经快照序列化往返存活（丢失 ⇒ 同一 operationId 重试绕过幂等、重复推进任务）");
        } finally {
            store.shutDown();
        }
    }

    /** 反射向账本字段注入一条记录（账本仅由事件回放写入，命令流无法直接构造）。 */
    @SuppressWarnings("unchecked")
    private static void setLedgerEntry(Class<?> owner, Object target, String fieldName, String operationId,
            String operationHash) throws Exception {
        Field field = owner.getDeclaredField(fieldName);
        field.setAccessible(true);
        Map<String, String> ledger = (Map<String, String>) field.get(target);
        if (ledger == null) {
            ledger = new HashMap<>();
            field.set(target, ledger);
        }
        ledger.put(operationId, operationHash);
    }

    /**
     * 在临时 UnitOfWork 中加载聚合状态（{@code repository.load} 要求当前线程存在 UoW； 生产路径由命令处理器建立，测试直载须自备）。
     */
    private static Maintenance loadState(Repository<Maintenance> repository, String aggregateId) {
        UnitOfWork<?> unitOfWork = DefaultUnitOfWork.startAndGet(GenericMessage.asMessage("snapshot-load-probe"));
        try {
            Maintenance state = repository.load(aggregateId).invoke(root -> root);
            unitOfWork.commit();
            return state;
        } catch (RuntimeException e) {
            unitOfWork.rollback(e);
            throw e;
        }
    }

    /**
     * 通过命令网关跑完整命令流：1 个创建 + changeCount 个变更，返回聚合标识。
     */
    private static MaintenanceId runCommandFlow(Repository<Maintenance> repository, int changeCount) {
        SimpleCommandBus commandBus = SimpleCommandBus.builder().build();
        AggregateAnnotationCommandHandler.<Maintenance>builder()
                .repository(repository)
                .aggregateType(Maintenance.class)
                .build()
                .subscribe(commandBus);
        DefaultCommandGateway gateway = DefaultCommandGateway.builder().commandBus(commandBus).build();

        // DSN-05：ID 生成属边界职责，值对象不再自产 ID；测试用固定可读标识（各测试独立事件存储，无唯一性约束）
        MaintenanceId id = MaintenanceId.of("snapshot-flow-maintenance");
        gateway.sendAndWait(new CreateMaintenanceCommand(id, PolicyId.of("snapshot-flow-policy"),
                CustomerId.of("snapshot-flow-customer"),
                MaintenanceType.POLICY_HOLDER_CHANGE, EffectiveTimeType.IMMEDIATE, LocalDateTime.now(), "快照机制测试",
                OPERATOR, TENANT_ID));
        for (int i = 1; i <= changeCount; i++) {
            gateway.sendAndWait(new AddMaintenanceChangeCommand(id, MaintenanceChangeType.MODIFY, "probeField" + i,
                    "before-" + i, "after-" + i, OPERATOR, TENANT_ID));
        }
        return id;
    }

    /**
     * 包装事件存储，模拟生产 JPA 引擎的读取语义并统计消费数： 读取流中的聚合状态载荷（快照）逐次深复制（生产每次读取都反序列化出新对象）， 其余消息原样透传；同时统计实际消费的<b>非快照</b>消息数——用于判据「带快照的加载是否真的跳过了快照覆盖的事件段」。
     */
    private static EventStore deserializingCountingEventStore(EventStore delegate, AtomicInteger nonSnapshotConsumed,
            JacksonSerializer serializer) {
        return (EventStore) Proxy.newProxyInstance(EventStore.class.getClassLoader(), new Class<?>[] { EventStore.class },
                (proxy, method, args) -> {
                    Object result = method.invoke(delegate, args);
                    if (result instanceof DomainEventStream stream) {
                        return deserializingCountingStream(stream, nonSnapshotConsumed, serializer);
                    }
                    return result;
                });
    }

    private static DomainEventStream deserializingCountingStream(DomainEventStream delegate,
            AtomicInteger nonSnapshotConsumed, JacksonSerializer serializer) {
        return new DomainEventStream() {
            @Override
            public boolean hasNext() {
                return delegate.hasNext();
            }

            @Override
            public DomainEventMessage<?> next() {
                DomainEventMessage<?> message = delegate.next();
                // 聚合类载荷即快照（普通事件 payload 恒为事件类，二者无交集）——复制以模拟生产的反序列化边界
                if (message.getPayload() instanceof Maintenance aggregate) {
                    return deserializeCopy(message, aggregate, serializer);
                }
                nonSnapshotConsumed.incrementAndGet();
                return message;
            }

            @Override
            public DomainEventMessage<?> peek() {
                // 🔴 peek 必须同样过复制边界：仓库以流首（peek）为聚合根种子恢复快照聚合
                // （AbstractAggregateFactory.fromSnapshot），只包装 next() 会让加载拿到存储对象本身。
                return copyAggregatePayload(delegate.peek());
            }

            @Override
            public Long getLastSequenceNumber() {
                return delegate.getLastSequenceNumber();
            }

            private DomainEventMessage<?> copyAggregatePayload(DomainEventMessage<?> message) {
                if (message != null && message.getPayload() instanceof Maintenance aggregate) {
                    return deserializeCopy(message, aggregate, serializer);
                }
                return message;
            }
        };
    }

    /** 经 Jackson 序列化往返复制聚合状态，重建等序号的领域事件消息（快照消息的其余分量逐项保留）。 */
    private static DomainEventMessage<?> deserializeCopy(DomainEventMessage<?> message, Maintenance aggregate,
            JacksonSerializer serializer) {
        SerializedObject<byte[]> serialized = serializer.serialize(aggregate, byte[].class);
        Maintenance copy = serializer.deserialize(new SimpleSerializedObject<>(serialized.getData(), byte[].class,
                Maintenance.class.getName(), null));
        return new GenericDomainEventMessage<>(message.getType(), message.getAggregateIdentifier(),
                message.getSequenceNumber(), copy, message.getMetaData(), message.getIdentifier(), message.getTimestamp());
    }
}
