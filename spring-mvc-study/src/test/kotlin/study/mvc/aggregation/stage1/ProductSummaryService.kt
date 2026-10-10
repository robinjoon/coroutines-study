package study.mvc.aggregation.stage1

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

data class ProductSummary(val id: Long, val price: Int, val stock: Int?)

class PriceUnavailableException(id: Long) : RuntimeException("price $id unavailable")
class StockUnavailableException(id: Long) : RuntimeException("stock $id unavailable")
class SummaryTimeoutException(id: Long) : RuntimeException("summary $id timed out")

interface PriceClient {
    suspend fun price(id: Long): Int
}

interface StockClient {
    suspend fun stock(id: Long): Int
}

/**
 * 상품 요약 서비스.
 * - 가격은 **필수** 정보다. 가격을 못 구하면 요약 전체가 실패한다.
 * - 재고는 **선택** 정보다. 재고를 못 구하면 stock = null 로 응답한다(부분 응답).
 */
class ProductSummaryService(
    private val priceClient: PriceClient,
    private val stockClient: StockClient,
) {

    suspend fun summary(id: Long): ProductSummary {
        // TODO(b): 가격과 재고를 동시에 조회하자.
        //  - 재고 조회가 StockUnavailableException으로 실패하면 stock = null (catch (e: Exception)처럼 넓게 잡으면 취소 신호까지 삼키니 주의)
        //  - 가격 조회가 실패하면 그 예외를 그대로 던지고, 진행 중인 재고 조회는 취소되어야 한다.
        TODO("가격과 재고를 병렬로 조회하도록 구현하세요")
    }

    suspend fun summaryWithDeadline(id: Long): ProductSummary {
        // TODO(b): summary와 같은 정책에 시간 예산을 더하자.
        //  - 재고 조회는 100ms 안에 끝나지 않으면 포기하고 stock = null
        //  - 요약 전체는 300ms 안에 끝나지 않으면 SummaryTimeoutException을 던진다.
        //  - 바깥에서 취소되거나 호출한 쪽이 건 timeout이 먼저 끝나면, 부분 응답이나 SummaryTimeoutException으로 바꾸지 말고 그대로 전파되어야 한다.
        TODO("시간 예산을 적용하도록 구현하세요")
    }

    companion object {
        const val STOCK_BUDGET_MILLIS = 100L
        const val TOTAL_BUDGET_MILLIS = 300L
    }
}

/**
 * 테스트용 가짜 클라이언트. 완성되어 있으니 수정하지 않는다.
 * 시작/종료/실패/취소를 events에 기록한다.
 */
class FakePriceClient(
    private val events: MutableList<String>,
    private val delayMillis: Long = 100,
    private val fail: Boolean = false,
) : PriceClient {
    override suspend fun price(id: Long): Int = record(events, "price", delayMillis, fail, { PriceUnavailableException(id) }) { 10_000 }
}

class FakeStockClient(
    private val events: MutableList<String>,
    private val delayMillis: Long = 100,
    private val fail: Boolean = false,
) : StockClient {
    override suspend fun stock(id: Long): Int = record(events, "stock", delayMillis, fail, { StockUnavailableException(id) }) { 7 }
}

private suspend fun <T> record(
    events: MutableList<String>,
    name: String,
    delayMillis: Long,
    fail: Boolean,
    failure: () -> Exception,
    value: () -> T,
): T {
    events += "$name:start"
    try {
        delay(delayMillis)
    } catch (e: CancellationException) {
        events += "$name:cancelled"
        throw e
    }
    if (fail) {
        events += "$name:failed"
        throw failure()
    }
    events += "$name:end"
    return value()
}
