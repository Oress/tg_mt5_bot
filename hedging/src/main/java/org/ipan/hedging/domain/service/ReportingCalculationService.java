package org.ipan.hedging.domain.service;

import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import org.ipan.hedging.domain.AppConstants;
import org.ipan.hedging.domain.model.*;
import org.ipan.hedging.domain.model.repository.*;
import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service
@AllArgsConstructor
public class ReportingCalculationService {
    private final static int SCALE_USD = 2; // keep this in sync with Google Sheets and rounding mode HALF_UP
    private final static int SCALE = 5; // keep this in sync with Google Sheets and rounding mode HALF_UP
    private final static int SCALE7 = 7;
    private final static RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP; // keep this in sync with Google Sheets and rounding mode HALF_UP

    private final AppUserInstrumentRepository appUserInstrumentRepository;
    private final OrderRepo orderRepo;
    private final ReportingCalculationRepository reportingCalculationRepository;
    private final UserBalanceActionRepo userBalanceActionRepo;
    private final UserRepo userRepo;

    @Async
    @Transactional
    public CompletableFuture<Void> recalculateAllForUser(Long userId) {
        AppUser user = userRepo.findById(userId).get();
        for (AppUserInstrument availableInstrument : user.getAvailableInstruments()) {
            recalculateAllForUserInstrument(availableInstrument.getId());
        }
        return CompletableFuture.completedFuture(null);
    }

    // TODO: add date instant parameter to allow recalculation for specific date range
    @Async
    @Transactional
    public CompletableFuture<Void> recalculateAllForUserInstrument(Long userInstrumentId) {
        try {
            AppUserInstrument userInstrument = appUserInstrumentRepository.findById(userInstrumentId).get();

            // TODO: has to be optimized to use pagination in the future
            List<Order> orders = orderRepo.findAllForReportingCalculation(userInstrumentId);
            List<UserBalanceAction> depositActions = Collections.emptyList(); //userBalanceActionRepo.findAllByUserOrderByDateDesc(userInstrument.getUser());

            // remove all
            reportingCalculationRepository.deleteAllOperationsByInstrumentId(userInstrumentId);
//            reportingCalculationRepository.deleteAllDepositsByUserId(userInstrument.getUser().getTelegramId());

            UserBalanceActionCalculator userBalanceActionCalculator = new DepositOperationCalculator();
            OperationCalculator operationCalculator = switch (userInstrument.getInstrument().getLabel()) {
                case AppConstants.EUR_USD -> new EurUsdOperationCalculator();
                case AppConstants.USD_CAD -> new UsdCadOperationCalculator();
                default -> throw new IllegalArgumentException("Unsupported instrument: " + userInstrument.getInstrument().getLabel());
            };

            List<ReportingCalculation> reportingCalculations = new ArrayList<>();
            int i = 0;
            int j = 0;
            ReportingCalculation prevRecord = null;
            while (i < orders.size() || j < depositActions.size()) {
                ReportingCalculation record;

                if (i < orders.size() && (j >= depositActions.size() || orders.get(i).getCreatedAt().isBefore(depositActions.get(j).getDate()))) {
                    // process order
                    Order order = orders.get(i);
                    record = operationCalculator.calculate(order, prevRecord);
                    i++;
                } else {
                    // process deposit action
                    UserBalanceAction balanceAction = depositActions.get(j);
                    record = userBalanceActionCalculator.calculate(balanceAction, prevRecord);
                    j++;
                }

                reportingCalculations.add(record);
                prevRecord = record;
            }

            reportingCalculationRepository.saveAll(reportingCalculations);
            return CompletableFuture.completedFuture(null);
        } catch (Exception e) {
            e.printStackTrace();
            return CompletableFuture.failedFuture(e);
        }
    }

    @Transactional
    public void calculateForOrder(Long orderId) {
        try {
            Order order = orderRepo.findById(orderId).get();
            AppUserInstrument userInstrument = order.getUserInstrument();

            ReportingCalculation lastRowForTheUserInstrument = reportingCalculationRepository.getLastRowForTheUserInstrument(userInstrument.getId(), Limit.of(1));

            OperationCalculator operationCalculator = switch (userInstrument.getInstrument().getLabel()) {
                case AppConstants.EUR_USD -> new EurUsdOperationCalculator();
                case AppConstants.USD_CAD -> new UsdCadOperationCalculator();
                default -> throw new IllegalArgumentException("Unsupported instrument: " + userInstrument.getInstrument().getLabel());
            };
            ReportingCalculation newRecord = operationCalculator.calculate(order, lastRowForTheUserInstrument);
            reportingCalculationRepository.save(newRecord);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    private interface UserBalanceActionCalculator {
        ReportingCalculation calculate(UserBalanceAction balanceAction, ReportingCalculation prevRecord);
    }

    private static class DepositOperationCalculator implements UserBalanceActionCalculator {
        @Override
        public ReportingCalculation calculate(UserBalanceAction balanceAction, ReportingCalculation prevRecord) {
            ReportingCalculation record = new ReportingCalculation();

            record.setUserBalanceAction(balanceAction);
//            record.setUserInstrument(balanceAction.getUserInstrument());
            record.setDepositPayment(balanceAction.getAmountForGoogleSheet());
            record.setDate(balanceAction.getDate());

            if (prevRecord != null) {
                record.setInPosition(prevRecord.getInPosition().setScale(SCALE, ROUNDING_MODE));
                record.setRate(prevRecord.getRate().setScale(SCALE7, ROUNDING_MODE));
                record.setDepositTotal(prevRecord.getDepositTotal().add(prevRecord.getDepositPayment()).setScale(SCALE, ROUNDING_MODE));
            }
            record.setBalance(record.getDepositTotal().add(record.getDepositPayment()).setScale(SCALE, ROUNDING_MODE));

            return record;
        }
    }


    private static abstract class OperationCalculator {
        public ReportingCalculation calculate(Order order, ReportingCalculation prevRecord) {
            ReportingCalculation record = new ReportingCalculation();
            record.setOrder(order);
            record.setUserInstrument(order.getUserInstrument());

            // raw values
            record.setDate(order.getCreatedAt());
            record.setAmount(order.getUserAmountForGoogleSheet().setScale(SCALE, ROUNDING_MODE));
            record.setFix(order.getPrice().setScale(SCALE, ROUNDING_MODE));

            // calculated values
            record.setUsdt(calculateUsdt(order, record, prevRecord).setScale(SCALE_USD, ROUNDING_MODE));
            record.setCommissionPercentage(order.getCommissionCoef().multiply(AppConstants.HUNDRED).setScale(SCALE, ROUNDING_MODE));
            record.setCommission(order.getCommissionCoef().multiply(record.getUsdt()).negate().setScale(SCALE_USD, ROUNDING_MODE));

            // in position
            if (prevRecord == null) {
                record.setInPosition(record.getAmount().setScale(SCALE, ROUNDING_MODE));
            } else {
                record.setInPosition(prevRecord.getInPosition().add(record.getAmount()).setScale(SCALE, ROUNDING_MODE));
            }

            // rate
            if (prevRecord == null) {
                record.setRate(record.getFix().setScale(SCALE7, ROUNDING_MODE));
            } else {
                if (order.isHedgeOut()) {
                    record.setRate(prevRecord.getRate().setScale(SCALE7, ROUNDING_MODE));
                } else {
                    // weighted avg
                    record.setRate(
                            prevRecord.getInPosition().multiply(prevRecord.getRate())
                                    .add(record.getAmount().multiply(record.getFix()))
                                    .divide(record.getInPosition(), SCALE7, ROUNDING_MODE)
                    );
                }
            }

            // deposit
            record.setDeposit(calculateDeposit(order, record).setScale(SCALE_USD, ROUNDING_MODE));

            if (record.getAmount().compareTo(BigDecimal.ZERO) > 0) {
                record.setPnl(record.getDeposit());
            }

            // deposit total
            if (prevRecord == null) {
                record.setDepositTotal(record.getDeposit().setScale(SCALE, ROUNDING_MODE));
            } else {
                record.setDepositTotal(
                        record.getDeposit()
                                .add(prevRecord.getDepositTotal().setScale(SCALE, ROUNDING_MODE))
                                .add(prevRecord.getDepositPayment().setScale(SCALE, ROUNDING_MODE))
                );
            }

            // balance
            record.setBalance(
                    record.getDepositTotal().add(record.getDepositPayment()).setScale(SCALE, ROUNDING_MODE)
            );

            return record;
        }

        protected abstract BigDecimal calculateDeposit(Order order, ReportingCalculation record);
        protected abstract BigDecimal calculateUsdt(Order order, ReportingCalculation record, ReportingCalculation prevRecord);
    }

    private static class EurUsdOperationCalculator extends OperationCalculator {
        @Override
        protected BigDecimal calculateUsdt(Order order, ReportingCalculation record, ReportingCalculation prevRecord) {
            return record.getAmount().multiply(order.getPrice());
        }

        @Override
        protected BigDecimal calculateDeposit(Order order, ReportingCalculation record) {
            if (order.isHedgeOut()) {
                return record.getAmount().negate()
                        .multiply(
                                record.getFix().subtract(record.getRate())
                        );
            } else {
                return record.getCommission().negate();
            }
        }
    }

    private static class UsdCadOperationCalculator extends OperationCalculator {

        @Override
        protected BigDecimal calculateUsdt(Order order, ReportingCalculation record, ReportingCalculation prevRecord) {
            return record.getAmount().divide(order.getPrice(), SCALE, ROUNDING_MODE);
        }

        @Override
        protected BigDecimal calculateDeposit(Order order, ReportingCalculation record) {
            if (order.isHedgeOut()) {
                return record.getFix().divide(record.getRate(), SCALE, ROUNDING_MODE)
                        .subtract(BigDecimal.ONE).multiply(record.getAmount())
                        .divide(record.getFix(), SCALE, ROUNDING_MODE);
            } else {
                return record.getCommission().negate();
            }
        }
    }
}
