package org.reactivestreams.tck;

import org.reactivestreams.Processor;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.reactivestreams.tck.TestEnvironment.ManualPublisher;
import org.reactivestreams.tck.TestEnvironment.ManualSubscriber;
import org.reactivestreams.tck.TestEnvironment.ManualSubscriberWithSubscriptionSupport;
import org.reactivestreams.tck.TestEnvironment.Promise;
import org.reactivestreams.tck.support.Function;
import org.reactivestreams.tck.support.SubscriberWhiteboxVerificationRules;
import org.reactivestreams.tck.support.PublisherVerificationRules;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.HashSet;
import java.util.Set;

public abstract class IdentityProcessorVerification<T> implements SubscriberWhiteboxVerificationRules, PublisherVerificationRules {

  private final TestEnvironment env;

  ////////////////////// DELEGATED TO SPECS //////////////////////

  // for delegating tests
  private final SubscriberWhiteboxVerification<T> subscriberVerification;

  // for delegating tests
  private final PublisherVerification<T> publisherVerification;

  ////////////////// END OF DELEGATED TO SPECS //////////////////

  // number of elements the processor under test must be able ot buffer,
  // without dropping elements. Defaults to `TestEnvironment.TEST_BUFFER_SIZE`.
  private final int processorBufferSize;

  /**
   * Test class must specify the expected time it takes for the publisher to
   * shut itself down when the the last downstream {@code Subscription} is cancelled.
   *
   * The processor will be required to be able to buffer {@code TestEnvironment.TEST_BUFFER_SIZE} elements.
   *
   * @param publisherShutdownTimeoutMillis expected time which a processor requires to shut itself down
   */
  @SuppressWarnings("unused")
  public IdentityProcessorVerification(final TestEnvironment env, long publisherShutdownTimeoutMillis) {
    this(env, publisherShutdownTimeoutMillis, TestEnvironment.TEST_BUFFER_SIZE);
  }

  /**
   * Test class must specify the expected time it takes for the publisher to
   * shut itself down when the the last downstream {@code Subscription} is cancelled.
   *
   * @param publisherShutdownTimeoutMillis expected time which a processor requires to shut itself down
   * @param processorBufferSize            number of elements the processor is required to be able to buffer.
   */
  public IdentityProcessorVerification(final TestEnvironment env, long publisherShutdownTimeoutMillis, int processorBufferSize) {
    this.env = env;
    this.processorBufferSize = processorBufferSize;

    this.subscriberVerification = new SubscriberWhiteboxVerification<T>(env) {
      @Override
      public Subscriber<T> createSubscriber(WhiteboxSubscriberProbe<T> probe) {
        return IdentityProcessorVerification.this.createSubscriber(probe);
      }

      @Override
      public Publisher<T> createHelperPublisher(long elements) {
        return IdentityProcessorVerification.this.createHelperPublisher(elements);
      }
    };

    publisherVerification = new PublisherVerification<T>(env, publisherShutdownTimeoutMillis) {
      @Override
      public Publisher<T> createPublisher(long elements) {
        return IdentityProcessorVerification.this.createPublisher(elements);
      }

      @Override
      public Publisher<T> createErrorStatePublisher() {
        return IdentityProcessorVerification.this.createErrorStatePublisher();
      }

      @Override
      public long maxElementsFromPublisher() {
        return IdentityProcessorVerification.this.maxElementsFromPublisher();
      }

      @Override
      public long boundedDepthOfOnNextAndRequestRecursion() {
        return IdentityProcessorVerification.this.boundedDepthOfOnNextAndRequestRecursion();
      }

      @Override
      public boolean skipStochasticTests() {
        return IdentityProcessorVerification.this.skipStochasticTests();
      }
    };
  }

  /**
   * This is the main method you must implement in your test incarnation.
   * It must create a Publisher, which simply forwards all stream elements from its upstream
   * to its downstream. It must be able to internally buffer the given number of elements.
   *
   * @param bufferSize number of elements the processor is required to be able to buffer.
   */
  public abstract Processor<T, T> createIdentityProcessor(int bufferSize);

  /**
   * Helper method required for running the Publisher rules against a Publisher.
   * It must create a Publisher for a stream with exactly the given number of elements.
   * If {@code elements} is {@code Long.MAX_VALUE} the produced stream must be infinite.
   *
   * The stream must not produce the same element twice (in case of an infinite stream this requirement
   * is relaxed to only apply to the elements that are actually requested during all tests).
   *
   * @param elements exact number of elements this publisher should emit,
   *                 unless equal to {@code Long.MAX_VALUE} in which case the stream should be effectively infinite
   */
  public abstract Publisher<T> createHelperPublisher(long elements);

  /**
   * Return a Publisher that immediately signals {@code onError} to incoming subscriptions,
   * or {@code null} in order to skip them.
   */
  public abstract Publisher<T> createErrorStatePublisher();

  /**
   * Override and return lower value if your Publisher is only able to produce a known number of elements.
   * For example, if it is designed to return at-most-one element, return {@code 1} from this method.
   *
   * Defaults to {@code Long.MAX_VALUE - 1}, meaning that the Publisher can be produce a huge but NOT an unbounded number of elements.
   *
   * To mark your Publisher will *never* signal an {@code onComplete} override this method and return {@code Long.MAX_VALUE},
   * which will result in *skipping all tests which require an onComplete to be triggered* (!).
   */
  public long maxElementsFromPublisher() {
    return Long.MAX_VALUE - 1;
  }

  /**
   * In order to verify rule 3.3 of the reactive streams spec, this number will be used to check if a
   * {@code Subscription} actually solves the "unbounded recursion" problem by not allowing the number of
   * recursive calls to exceed the number returned by this method.
   *
   * @see <a href="https://github.com/reactive-streams/reactive-streams#3.3">reactive streams spec, rule 3.3</a>
   * @see PublisherVerification#required___spec303_mustNotAllowUnboundedRecursion()
   */
  public long boundedDepthOfOnNextAndRequestRecursion() {
    return 1;
  }

  /**
   * Override and return {@code true} in order to skip executing tests marked as {@code Stochastic}.
   * Such tests MAY sometimes fail even though the impl
   */
  public boolean skipStochasticTests() {
    return false;
  }

  /**
   * Describes the tested implementation in terms of how many subscribers they can support.
   * Some tests require the {@code Publisher} under test to support multiple Subscribers,
   * yet the spec does not require all publishers to be able to do so, thus – if an implementation
   * supports only a limited number of subscribers (e.g. only 1 subscriber, also known as "no fanout")
   * you MUST return that number from this method by overriding it.
   */
  public long maxSupportedSubscribers() {
      return Long.MAX_VALUE;
  }

  ////////////////////// TEST ENV CLEANUP /////////////////////////////////////

  @BeforeMethod
  public void setUp() throws Exception {
    publisherVerification.setUp();
    subscriberVerification.setUp();
  }

  ////////////////////// PUBLISHER RULES VERIFICATION ///////////////////////////

  // A Processor
  //   must obey all Publisher rules on its publishing side
  public Publisher<T> createPublisher(long elements) {
    final Processor<T, T> processor = createIdentityProcessor(processorBufferSize);
    final Publisher<T> pub = createHelperPublisher(elements);
    pub.subscribe(processor);
    return processor; // we run the PublisherVerification against this
  }

  @Override @Test
  public void required___validate_maxElementsFromPublisher() throws Exception {
    publisherVerification.required___validate_maxElementsFromPublisher();
  }

  @Override @Test
  public void required___validate_boundedDepthOfOnNextAndRequestRecursion() throws Exception {
    publisherVerification.required___validate_boundedDepthOfOnNextAndRequestRecursion();
  }

  /////////////////////// DELEGATED TESTS, A PROCESSOR "IS A" PUBLISHER //////////////////////
  // Verifies rule: https://github.com/reactive-streams/reactive-streams#4.1

  @Test
  public void required___createPublisher1MustProduceAStreamOfExactly1Element() throws Throwable {
    publisherVerification.required___createPublisher1MustProduceAStreamOfExactly1Element();
  }

  @Test
  public void required___createPublisher3MustProduceAStreamOfExactly3Elements() throws Throwable {
    publisherVerification.required___createPublisher3MustProduceAStreamOfExactly3Elements();
  }

  @Override @Test
  public void required___spec101_subscriptionRequestMustResultInTheCorrectNumberOfProducedElements() throws Throwable {
    publisherVerification.required___spec101_subscriptionRequestMustResultInTheCorrectNumberOfProducedElements();
  }

  @Override @Test
  public void required___spec102_maySignalLessThanRequestedAndTerminateSubscription() throws Throwable {
    publisherVerification.required___spec102_maySignalLessThanRequestedAndTerminateSubscription();
  }

  @Override @Test
  public void stochastic___spec103_mustSignalOnMethodsSequentially() throws Throwable {
    publisherVerification.stochastic___spec103_mustSignalOnMethodsSequentially();
  }

  @Override @Test
  public void optional___spec104_mustSignalOnErrorWhenFails() throws Throwable {
    publisherVerification.optional___spec104_mustSignalOnErrorWhenFails();
  }

  @Override @Test
  public void required___spec105_mustSignalOnCompleteWhenFiniteStreamTerminates() throws Throwable {
    publisherVerification.required___spec105_mustSignalOnCompleteWhenFiniteStreamTerminates();
  }

  @Override @Test
  public void unverified___spec106_mustConsiderSubscriptionCancelledAfterOnErrorOrOnCompleteHasBeenCalled() throws Throwable {
    publisherVerification.unverified___spec106_mustConsiderSubscriptionCancelledAfterOnErrorOrOnCompleteHasBeenCalled();
  }

  @Override @Test
  public void required___spec107_mustNotEmitFurtherSignalsOnceOnCompleteHasBeenSignalled() throws Throwable {
    publisherVerification.required___spec107_mustNotEmitFurtherSignalsOnceOnCompleteHasBeenSignalled();
  }

  @Override @Test
  public void unverified___spec107_mustNotEmitFurtherSignalsOnceOnErrorHasBeenSignalled() throws Throwable {
    publisherVerification.unverified___spec107_mustNotEmitFurtherSignalsOnceOnErrorHasBeenSignalled();
  }

  @Override @Test
  public void unverified___spec108_possiblyCanceledSubscriptionShouldNotReceiveOnErrorOrOnCompleteSignals() throws Throwable {
    publisherVerification.unverified___spec108_possiblyCanceledSubscriptionShouldNotReceiveOnErrorOrOnCompleteSignals();
  }

  @Override @Test
  public void unverified___spec109_subscribeShouldNotThrowNonFatalThrowable() throws Throwable {
    publisherVerification.unverified___spec109_subscribeShouldNotThrowNonFatalThrowable();
  }

  @Override @Test
  public void unverified___spec110_rejectASubscriptionRequestIfTheSameSubscriberSubscribesTwice() throws Throwable {
    publisherVerification.unverified___spec110_rejectASubscriptionRequestIfTheSameSubscriberSubscribesTwice();
  }

  @Override @Test
  public void optional___spec111_maySupportMultiSubscribe() throws Throwable {
    publisherVerification.optional___spec111_maySupportMultiSubscribe();
  }

  @Override @Test
  public void optional___spec112_mayRejectCallsToSubscribeIfPublisherIsUnableOrUnwillingToServeThemRejectionMustTriggerOnErrorInsteadOfOnSubscribe() throws Throwable {
    publisherVerification.optional___spec112_mayRejectCallsToSubscribeIfPublisherIsUnableOrUnwillingToServeThemRejectionMustTriggerOnErrorInsteadOfOnSubscribe();
  }

  @Override @Test
  public void required___spec113_mustProduceTheSameElementsInTheSameSequenceToAllOfItsSubscribersWhenRequestingOneByOne() throws Throwable {
    publisherVerification.required___spec113_mustProduceTheSameElementsInTheSameSequenceToAllOfItsSubscribersWhenRequestingOneByOne();
  }

  @Override @Test
  public void required___spec113_mustProduceTheSameElementsInTheSameSequenceToAllOfItsSubscribersWhenRequestingManyUpfront() throws Throwable {
    publisherVerification.required___spec113_mustProduceTheSameElementsInTheSameSequenceToAllOfItsSubscribersWhenRequestingManyUpfront();
  }

  @Override @Test
  public void required___spec113_mustProduceTheSameElementsInTheSameSequenceToAllOfItsSubscribersWhenRequestingManyUpfrontAndCompleteAsExpected() throws Throwable {
    publisherVerification.required___spec113_mustProduceTheSameElementsInTheSameSequenceToAllOfItsSubscribersWhenRequestingManyUpfrontAndCompleteAsExpected();
  }

  @Override @Test
  public void required___spec302_mustAllowSynchronousRequestCallsFromOnNextAndOnSubscribe() throws Throwable {
    publisherVerification.required___spec302_mustAllowSynchronousRequestCallsFromOnNextAndOnSubscribe();
  }

  @Override @Test
  public void required___spec303_mustNotAllowUnboundedRecursion() throws Throwable {
    publisherVerification.required___spec303_mustNotAllowUnboundedRecursion();
  }

  @Override @Test
  public void unverified___spec304_requestShouldNotPerformHeavyComputations() throws Exception {
    publisherVerification.unverified___spec304_requestShouldNotPerformHeavyComputations();
  }

  @Override @Test
  public void unverified___spec305_cancelMustNotSynchronouslyPerformHeavyCompuatation() throws Exception {
    publisherVerification.unverified___spec305_cancelMustNotSynchronouslyPerformHeavyCompuatation();
  }

  @Override @Test
  public void required___spec306_afterSubscriptionIsCancelledRequestMustBeNops() throws Throwable {
    publisherVerification.required___spec306_afterSubscriptionIsCancelledRequestMustBeNops();
  }

  @Override @Test
  public void required___spec307_afterSubscriptionIsCancelledAdditionalCancelationsMustBeNops() throws Throwable {
    publisherVerification.required___spec307_afterSubscriptionIsCancelledAdditionalCancelationsMustBeNops();
  }

  @Override @Test
  public void required___spec309_requestZeroMustSignalIllegalArgumentException() throws Throwable {
    publisherVerification.required___spec309_requestZeroMustSignalIllegalArgumentException();
  }

  @Override @Test
  public void required___spec309_requestNegativeNumberMustSignalIllegalArgumentException() throws Throwable {
    publisherVerification.required___spec309_requestNegativeNumberMustSignalIllegalArgumentException();
  }

  @Override @Test
  public void required___spec312_cancelMustMakeThePublisherToEventuallyStopSignaling() throws Throwable {
    publisherVerification.required___spec312_cancelMustMakeThePublisherToEventuallyStopSignaling();
  }

  @Override @Test
  public void required___spec313_cancelMustMakeThePublisherEventuallyDropAllReferencesToTheSubscriber() throws Throwable {
    publisherVerification.required___spec313_cancelMustMakeThePublisherEventuallyDropAllReferencesToTheSubscriber();
  }

  @Override @Test
  public void required___spec317_mustSupportAPendingElementCountUpToLongMaxValue() throws Throwable {
    publisherVerification.required___spec317_mustSupportAPendingElementCountUpToLongMaxValue();
  }

  @Override @Test
  public void required___spec317_mustSupportACumulativePendingElementCountUpToLongMaxValue() throws Throwable {
    publisherVerification.required___spec317_mustSupportACumulativePendingElementCountUpToLongMaxValue();
  }

  @Override @Test
  public void required___spec317_mustSignalOnErrorWhenPendingAboveLongMaxValue() throws Throwable {
    publisherVerification.required___spec317_mustSignalOnErrorWhenPendingAboveLongMaxValue();
  }

  // Verifies rule: https://github.com/reactive-streams/reactive-streams#1.4
  // for multiple subscribers
  @Test 
  public void required___spec104_mustCallOnErrorOnAllItsSubscribersIfItEncountersANonRecoverableError() throws Throwable {
    optionalMultipleSubscribersTest(2, new Function<Long,TestSetup>() {
      @Override
      public TestSetup apply(Long aLong) throws Throwable {
        return new TestSetup(env, processorBufferSize) {{
          final ManualSubscriberWithErrorCollection<T> sub1 = new ManualSubscriberWithErrorCollection<T>(env);
          env.subscribe(processor, sub1);

          final ManualSubscriberWithErrorCollection<T> sub2 = new ManualSubscriberWithErrorCollection<T>(env);
          env.subscribe(processor, sub2);

          sub1.request(1);
          expectRequest();
          final T x = sendNextTFromUpstream();
          expectNextElement(sub1, x);
          sub1.request(1);

          // sub1 has received one element, and has one demand pending
          // sub2 has not yet requested anything

          final Exception ex = new RuntimeException("Test exception");
          sendError(ex);
          sub1.expectError(ex);
          sub2.expectError(ex);

          env.verifyNoAsyncErrors();
        }};
      }
    });
  }

  ////////////////////// SUBSCRIBER RULES VERIFICATION ///////////////////////////
  // Verifies rule: https://github.com/reactive-streams/reactive-streams#4.1

  // A Processor
  //   must obey all Subscriber rules on its consuming side
  public Subscriber<T> createSubscriber(final SubscriberWhiteboxVerification.WhiteboxSubscriberProbe<T> probe) {
    final Processor<T, T> processor = createIdentityProcessor(processorBufferSize);
    processor.subscribe(
        new Subscriber<T>() {
          private final Promise<Subscription> subs = new Promise<Subscription>(env);

          @Override
          public void onSubscribe(final Subscription subscription) {
            env.debug(String.format("whiteboxSubscriber::onSubscribe(%s)", subscription));
            if (subs.isCompleted()) subscription.cancel(); // the Probe must also pass subscriber verification

            probe.registerOnSubscribe(new SubscriberWhiteboxVerification.SubscriberPuppet() {

              @Override
              public void triggerRequest(long elements) {
                subscription.request(elements);
              }

              @Override
              public void signalCancel() {
                subscription.cancel();
              }
            });
          }

          @Override
          public void onNext(T element) {
            env.debug(String.format("whiteboxSubscriber::onNext(%s)", element));
            probe.registerOnNext(element);
          }

          @Override
          public void onComplete() {
            env.debug("whiteboxSubscriber::onComplete()");
            probe.registerOnComplete();
          }

          @Override
          public void onError(Throwable cause) {
            env.debug(String.format("whiteboxSubscriber::onError(%s)", cause));
            probe.registerOnError(cause);
          }
        });

    return processor; // we run the SubscriberVerification against this
  }

  ////////////////////// OTHER RULE VERIFICATION ///////////////////////////

  // A Processor
  //   must immediately pass on `onError` events received from its upstream to its downstream
  @Test
  public void mustImmediatelyPassOnOnErrorEventsReceivedFromItsUpstreamToItsDownstream() throws Exception {
    new TestSetup(env, processorBufferSize) {{
      final ManualSubscriberWithErrorCollection<T> sub = new ManualSubscriberWithErrorCollection<T>(env);
      env.subscribe(processor, sub);

      final Exception ex = new RuntimeException("Test exception");
      sendError(ex);
      sub.expectError(ex); // "immediately", i.e. without a preceding request

      env.verifyNoAsyncErrors();
    }};
  }

  /////////////////////// DELEGATED TESTS, A PROCESSOR "IS A" SUBSCRIBER //////////////////////
  // Verifies rule: https://github.com/reactive-streams/reactive-streams#4.1

  @Test
  public void required___exerciseWhiteboxHappyPath() throws Throwable {
    subscriberVerification.required___exerciseWhiteboxHappyPath();
  }

  @Override @Test
  public void required___spec201_mustSignalDemandViaSubscriptionRequest() throws Throwable {
    subscriberVerification.required___spec201_mustSignalDemandViaSubscriptionRequest();
  }

  @Override @Test
  public void unverified___spec202_shouldAsynchronouslyDispatch() throws Exception {
    subscriberVerification.unverified___spec202_shouldAsynchronouslyDispatch();
  }

  @Override @Test
  public void required___spec203_mustNotCallMethodsOnSubscriptionOrPublisherInOnComplete() throws Throwable {
    subscriberVerification.required___spec203_mustNotCallMethodsOnSubscriptionOrPublisherInOnComplete();
  }

  @Override @Test
  public void required___spec203_mustNotCallMethodsOnSubscriptionOrPublisherInOnError() throws Throwable {
    subscriberVerification.required___spec203_mustNotCallMethodsOnSubscriptionOrPublisherInOnError();
  }

  @Override @Test
  public void unverified___spec204_mustConsiderTheSubscriptionAsCancelledInAfterRecievingOnCompleteOrOnError() throws Exception {
    subscriberVerification.unverified___spec204_mustConsiderTheSubscriptionAsCancelledInAfterRecievingOnCompleteOrOnError();
  }

  @Override @Test
  public void required___spec205_mustCallSubscriptionCancelIfItAlreadyHasAnSubscriptionAndReceivesAnotherOnSubscribeSignal() throws Exception {
    subscriberVerification.required___spec205_mustCallSubscriptionCancelIfItAlreadyHasAnSubscriptionAndReceivesAnotherOnSubscribeSignal();
  }

  @Override @Test
  public void unverified___spec206_mustCallSubscriptionCancelIfItIsNoLongerValid() throws Exception {
    subscriberVerification.unverified___spec206_mustCallSubscriptionCancelIfItIsNoLongerValid();
  }

  @Override @Test
  public void unverified___spec207_mustEnsureAllCallsOnItsSubscriptionTakePlaceFromTheSameThreadOrTakeCareOfSynchronization() throws Exception {
    subscriberVerification.unverified___spec207_mustEnsureAllCallsOnItsSubscriptionTakePlaceFromTheSameThreadOrTakeCareOfSynchronization();
  }

  @Override @Test
  public void required___spec208_mustBePreparedToReceiveOnNextSignalsAfterHavingCalledSubscriptionCancel() throws Throwable {
    subscriberVerification.required___spec208_mustBePreparedToReceiveOnNextSignalsAfterHavingCalledSubscriptionCancel();
  }

  @Override @Test
  public void required___spec209_mustBePreparedToReceiveAnOnCompleteSignalWithPrecedingRequestCall() throws Throwable {
    subscriberVerification.required___spec209_mustBePreparedToReceiveAnOnCompleteSignalWithPrecedingRequestCall();
  }

  @Override @Test
  public void required___spec209_mustBePreparedToReceiveAnOnCompleteSignalWithoutPrecedingRequestCall() throws Throwable {
    subscriberVerification.required___spec209_mustBePreparedToReceiveAnOnCompleteSignalWithoutPrecedingRequestCall();
  }

  @Override @Test
  public void required___spec210_mustBePreparedToReceiveAnOnErrorSignalWithPrecedingRequestCall() throws Throwable {
    subscriberVerification.required___spec210_mustBePreparedToReceiveAnOnErrorSignalWithPrecedingRequestCall();
  }

  @Override @Test
  public void required___spec210_mustBePreparedToReceiveAnOnErrorSignalWithoutPrecedingRequestCall() throws Throwable {
    subscriberVerification.required___spec210_mustBePreparedToReceiveAnOnErrorSignalWithoutPrecedingRequestCall();
  }

  @Override @Test
  public void unverified___spec211_mustMakeSureThatAllCallsOnItsMethodsHappenBeforeTheProcessingOfTheRespectiveEvents() throws Exception {
    subscriberVerification.unverified___spec211_mustMakeSureThatAllCallsOnItsMethodsHappenBeforeTheProcessingOfTheRespectiveEvents();
  }

  @Override @Test
  public void optional___spec212_mustNotCallOnSubscribeMoreThanOnceBasedOnObjectEquality_specViolation() throws Throwable {
    subscriberVerification.optional___spec212_mustNotCallOnSubscribeMoreThanOnceBasedOnObjectEquality_specViolation();
  }

  @Override @Test
  public void unverified___spec213_failingOnSignalInvocation() throws Exception {
    subscriberVerification.unverified___spec213_failingOnSignalInvocation();
  }

  @Override @Test
  public void unverified___spec301_mustNotBeCalledOutsideSubscriberContext() throws Exception {
    subscriberVerification.unverified___spec301_mustNotBeCalledOutsideSubscriberContext();
  }

  @Override @Test
  public void required___spec308_requestMustRegisterGivenNumberElementsToBeProduced() throws Throwable {
    subscriberVerification.required___spec308_requestMustRegisterGivenNumberElementsToBeProduced();
  }

  @Override @Test
  public void unverified___spec310_requestMaySynchronouslyCallOnNextOnSubscriber() throws Exception {
    subscriberVerification.unverified___spec310_requestMaySynchronouslyCallOnNextOnSubscriber();
  }

  @Override @Test
  public void unverified___spec311_requestMaySynchronouslyCallOnCompleteOrOnError() throws Exception {
    subscriberVerification.unverified___spec311_requestMaySynchronouslyCallOnCompleteOrOnError();
  }

  @Override @Test
  public void unverified___spec314_cancelMayCauseThePublisherToShutdownIfNoOtherSubscriptionExists() throws Exception {
    subscriberVerification.unverified___spec314_cancelMayCauseThePublisherToShutdownIfNoOtherSubscriptionExists();
  }

  @Override @Test
  public void unverified___spec315_cancelMustNotThrowExceptionAndMustSignalOnError() throws Exception {
    subscriberVerification.unverified___spec315_cancelMustNotThrowExceptionAndMustSignalOnError();
  }

  @Override @Test
  public void unverified___spec316_requestMustNotThrowExceptionAndMustOnErrorTheSubscriber() throws Exception {
    subscriberVerification.unverified___spec316_requestMustNotThrowExceptionAndMustOnErrorTheSubscriber();
  }

  /////////////////////// ADDITIONAL "COROLLARY" TESTS //////////////////////

  // A Processor
  //   must trigger `requestFromUpstream` for elements that have been requested 'long ago'
  @Test
  public void required___mustRequestFromUpstreamForElementsThatHaveBeenRequestedLongAgo() throws Throwable {
    optionalMultipleSubscribersTest(2, new Function<Long,TestSetup>() {
      @Override
      public TestSetup apply(Long subscribers) throws Throwable {
        return new TestSetup(env, processorBufferSize) {{
          ManualSubscriber<T> sub1 = newSubscriber();
          sub1.request(20);

          long totalRequests = expectRequest();
          final T x = sendNextTFromUpstream();
          expectNextElement(sub1, x);

          if (totalRequests == 1) {
            totalRequests += expectRequest();
          }
          final T y = sendNextTFromUpstream();
          expectNextElement(sub1, y);

          if (totalRequests == 2) {
            totalRequests += expectRequest();
          }

          final ManualSubscriber<T> sub2 = newSubscriber();

          // sub1 now has 18 pending
          // sub2 has 0 pending

          final T z = sendNextTFromUpstream();
          expectNextElement(sub1, z);
          sub2.expectNone(); // since sub2 hasn't requested anything yet

          sub2.request(1);
          expectNextElement(sub2, z);

          if (totalRequests == 3) {
            expectRequest();
          }

          // to avoid error messages during test harness shutdown
          sendCompletion();
          sub1.expectCompletion(env.defaultTimeoutMillis());
          sub2.expectCompletion(env.defaultTimeoutMillis());

          env.verifyNoAsyncErrors();
        }};
      }
    });
  }

  /////////////////////// TEST INFRASTRUCTURE //////////////////////

  public void notVerified() {
    publisherVerification.notVerified();
  }

  public void notVerified(String message) {
    publisherVerification.notVerified(message);
  }

  /**
   * Test for feature that REQUIRES multiple subscribers to be supported by Publisher.
   */
  public void optionalMultipleSubscribersTest(long requiredSubscribersSupport, Function<Long, TestSetup> body) throws Throwable {
    if (requiredSubscribersSupport > maxSupportedSubscribers())
      notVerified(String.format("The Publisher under test only supports %d subscribers, while this test requires at least %d to run.",
                                maxSupportedSubscribers(), requiredSubscribersSupport));
    else body.apply(requiredSubscribersSupport);
  }

  public abstract class TestSetup extends ManualPublisher<T> {
    final private ManualSubscriber<T> tees; // gives us access to an infinite stream of T values
    private Set<T> seenTees = new HashSet<T>();

    final Processor<T, T> processor;

    public TestSetup(TestEnvironment env, int testBufferSize) throws InterruptedException {
      super(env);
      tees = env.newManualSubscriber(createHelperPublisher(Long.MAX_VALUE));
      processor = createIdentityProcessor(testBufferSize);
      subscribe(processor);
    }

    public ManualSubscriber<T> newSubscriber() throws InterruptedException {
      return env.newManualSubscriber(processor);
    }

    public T nextT() throws InterruptedException {
      final T t = tees.requestNextElement();
      if (seenTees.contains(t)) {
        env.flop(String.format("Helper publisher illegally produced the same element %s twice", t));
      }
      seenTees.add(t);
      return t;
    }

    public void expectNextElement(ManualSubscriber<T> sub, T expected) throws InterruptedException {
      final T elem = sub.nextElement(String.format("timeout while awaiting %s", expected));
      if (!elem.equals(expected)) {
        env.flop(String.format("Received `onNext(%s)` on downstream but expected `onNext(%s)`", elem, expected));
      }
    }

    public T sendNextTFromUpstream() throws InterruptedException {
      final T x = nextT();
      sendNext(x);
      return x;
    }
  }

  public class ManualSubscriberWithErrorCollection<A> extends ManualSubscriberWithSubscriptionSupport<A> {
    Promise<Throwable> error;

    public ManualSubscriberWithErrorCollection(TestEnvironment env) {
      super(env);
      error = new Promise<Throwable>(env);
    }

    @Override
    public void onError(Throwable cause) {
      error.complete(cause);
    }

    public void expectError(Throwable cause) throws InterruptedException {
      expectError(cause, env.defaultTimeoutMillis());
    }

    @SuppressWarnings("ThrowableResultOfMethodCallIgnored")
    public void expectError(Throwable cause, long timeoutMillis) throws InterruptedException {
      error.expectCompletion(timeoutMillis, "Did not receive expected error on downstream");
      if (!cause.equals(error.value())) {
        env.flop(String.format("Expected error %s but got %s", cause, error.value()));
      }
    }
  }
}