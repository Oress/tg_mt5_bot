export const formatCurrency = (amount, currency = 'USD', locale = 'en-US', maximumFractionDigits = 2) => {
  return new Intl.NumberFormat(locale, {
    style: 'currency',
    currency: currency,
    maximumFractionDigits: maximumFractionDigits,
  }).format(amount);
};