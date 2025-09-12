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
                record.setInPosition(prevRecord.getInPosition());
                record.setRate(prevRecord.getRate());
                record.setDepositTotal(prevRecord.getDepositTotal().add(prevRecord.getDepositPayment()));
            }
            record.setBalance(record.getDepositTotal().add(record.getDepositPayment()));

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
            record.setAmount(order.getUserAmountForGoogleSheet());
            record.setFix(order.getPrice());

            // calculated values
            record.setUsdt(calculateUsdt(order, record, prevRecord));
            record.setCommissionPercentage(order.getCommissionCoef().multiply(AppConstants.HUNDRED));
            record.setCommission(order.getCommissionCoef().multiply(record.getUsdt()).negate());

            // in position
            if (prevRecord == null) {
                record.setInPosition(record.getAmount());
            } else {
                record.setInPosition(prevRecord.getInPosition().add(record.getAmount()));
            }

            // rate
            if (prevRecord == null) {
                record.setRate(record.getFix());
            } else {
                if (order.isHedgeOut()) {
                    record.setRate(prevRecord.getRate());
                } else {
                    // weighted avg
                    record.setRate(
                            prevRecord.getInPosition().multiply(prevRecord.getRate())
                                    .add(record.getAmount().multiply(record.getFix()))
                                    .divide(record.getInPosition(), 8, RoundingMode.HALF_DOWN)
                    );
                }
            }

            // deposit
            record.setDeposit(calculateDeposit(order, record));

            // deposit total
            if (prevRecord == null) {
                record.setDepositTotal(record.getDeposit());
            } else {
                record.setDepositTotal(
                        record.getDeposit()
                                .add(prevRecord.getDepositTotal())
                                .add(prevRecord.getDepositPayment())
                );
            }

            // balance
            record.setBalance(
                    record.getDepositTotal().add(record.getDepositPayment())
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
            return record.getAmount().divide(order.getPrice(), 8, RoundingMode.HALF_DOWN);
        }

        @Override
        protected BigDecimal calculateDeposit(Order order, ReportingCalculation record) {
            if (order.isHedgeOut()) {
                return record.getFix().divide(record.getRate(), 8, RoundingMode.HALF_DOWN)
                        .subtract(BigDecimal.ONE).multiply(record.getAmount())
                        .divide(record.getFix(), 8, RoundingMode.HALF_DOWN);
            } else {
                return record.getCommission().negate();
            }
        }
    }
}
