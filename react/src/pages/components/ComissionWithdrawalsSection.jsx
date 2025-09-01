import {useEffect, useState} from "react";
import api from "../../api.js";
import {Button, Divider, Flex, InputNumber, Modal, Radio, Select, Space, Spin, Table, Typography} from "antd";
import {formatCurrency} from "../../utils.js";
import {PlusOutlined} from "@ant-design/icons";

export function CommissionWithdrawalsSection({userId, refreshTrigger}) {
  const [commissions, setCommissions] = useState([]);
  const [isLoading, setIsLoading] = useState(true);
  const [isDialogOpen, setIsDialogOpen] = useState(false);
  const [isDeleteConfirmOpen, setIsDeleteConfirmOpen] = useState(false);
  const [currentTransaction, setCurrentTransaction] = useState(null);
  const [transactionToDelete, setTransactionToDelete] = useState(null);
  const [amount, setAmount] = useState('');
  const [date, setDate] = useState(new Date());
  const [instrument, setInstrument] = useState('USD/CAD');
  const [errors, setErrors] = useState({});

  const fetchTransactions = async () => {
    setIsLoading(true);
    try {
      const response = await api.get(`/users/${userId}/commission-withdrawals`);
      setCommissions(response.data.data || []);
    } catch (error) {
      console.error('Error fetching deposits:', error);
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    fetchTransactions();
  }, [userId, refreshTrigger]);

  const handleOpenDialog = (transaction = null) => {
    // Reset errors
    setErrors({});

    const now = transaction ? new Date(transaction.date) : new Date();

    if (transaction) {
      // Edit mode
      setCurrentTransaction(transaction);
      setAmount(transaction.amount);
      setDate(now);
      setInstrument(transaction.type)
    } else {
      // Add mode
      setCurrentTransaction(null);
      setAmount('');
      setDate(now);
      setInstrument('USD/CAD');
    }
    setIsDialogOpen(true);
  };

  const handleCloseDialog = () => {
    setIsDialogOpen(false);
    setErrors({});
  };

  const handleOpenDeleteConfirm = (transaction) => {
    setTransactionToDelete(transaction);
    setIsDeleteConfirmOpen(true);
  };

  const handleCloseDeleteConfirm = () => {
    setIsDeleteConfirmOpen(false);
    setTransactionToDelete(null);
  };

  const handleDelete = async () => {
    if (!transactionToDelete) return;

    try {
      await api.delete(`/users/${userId}/commission-withdrawals/${transactionToDelete.id}`);

      // Refresh the commissions list
      fetchTransactions();
      handleCloseDeleteConfirm();
    } catch (error) {
      console.error('Error deleting transaction:', error);
    }
  };

  const handleSubmit = async () => {
    // Reset errors
    setErrors({});

    // Validate fields
    const newErrors = {};

    if (!amount || parseFloat(amount) <= 0) {
      newErrors.amount = 'Пожалуйста, введите сумму больше нуля';
    }

    if (!date) {
      newErrors.date = 'Пожалуйста, выберите дату';
    }

    // If there are errors, display them and stop submission
    if (Object.keys(newErrors).length > 0) {
      setErrors(newErrors);
      return;
    }

    try {
      if (currentTransaction) {
        // Update existing transaction
        await api.put(`/users/${userId}/commission-withdrawals/${currentTransaction.id}`, {
          amount: amount,
          instrument: instrument,
          date: new Date(date)
        });
      } else {
        // Create new transaction
        await api.post(`/users/${userId}/commission-withdrawals`, {
          amount: amount,
          instrument: instrument,
          date: new Date(date)
        });
      }

      // Refresh the commissions list
      fetchTransactions();
      handleCloseDialog();
    } catch (error) {
      console.error('Error saving transaction:', error);
    }
  };

  const formatDate = (dateString) => {
    const dt = new Date(dateString);
    const hour = dt.getHours().toString().padStart(2, '0');
    const minute = dt.getMinutes().toString().padStart(2, '0');
    const day = dt.getDate().toString().padStart(2, '0');
    const month = (dt.getMonth() + 1).toString().padStart(2, '0');
    const year = dt.getFullYear();
    return `${day}.${month}.${year} ${hour}:${minute}`;
  };

  const columns = [
    {
      title: 'Сумма',
      key: 'amount',
      width: 150,
      render: (_, transaction) => (
        <Typography.Text strong>
          {formatCurrency(transaction.amount, 'USD')}
        </Typography.Text>
      ),
    },
    {
      title: 'Инструмент',
      key: 'instrument',
      dataIndex: 'instrument',
      width: 150,
    },
    {
      title: 'Дата',
      dataIndex: 'date',
      key: 'date',
      width: 200,
      render: (date) => formatDate(date),
    },
    /*    {
          title: 'Действия',
          key: 'actions',
          width: 120,
          render: (_, transaction) => (
            <Space>
              <Button
                type="text"
                icon={<EditOutlined />}
                onClick={() => handleOpenDialog(transaction)}
              />
              <Button
                type="text"
                danger
                icon={<DeleteOutlined />}
                onClick={() => handleOpenDeleteConfirm(transaction)}
              />
            </Space>
          ),
        }*/,
  ];

  return (
    <>
      <Space direction="vertical" size="middle" style={{width: '100%'}}>
        <div className="flex flex-col sm:flex-row justify-between items-center gap-2">
          <Typography.Title level={4} style={{margin: 0}}>
            Выводы комиссий
          </Typography.Title>
          <Button
            type="default"
            icon={<PlusOutlined/>}
            onClick={() => handleOpenDialog()}
          >
            Добавить запись
          </Button>
        </div>
        <Divider style={{margin: 0}}/>

        {isLoading ? (
          <Flex justify="center" style={{padding: '40px 0'}}>
            <Spin size="large"/>
          </Flex>
        ) : commissions.length > 0 ? (
          <Table
            style={{maxHeight: '350px'}}
            columns={columns}
            dataSource={commissions}
            pagination={{pageSize: 20}}
            rowKey="id"
            // scroll={{ x: 'max-content' }}
            scroll={{y: 55 * 5}}
            size="small"
          />
        ) : (
          <Flex justify="center" style={{padding: '40px 0'}}>
            <Typography.Text type="secondary">
              Нет записей о выводах комиссий для этого клиента
            </Typography.Text>
          </Flex>
        )}
      </Space>

      <Modal
        title={currentTransaction ? 'Редактировать запись' : 'Добавить новую запись'}
        open={isDialogOpen}
        onCancel={() => setIsDialogOpen(false)}
        footer={[
          <Button key="cancel" onClick={() => setIsDialogOpen(false)}>
            Отмена
          </Button>,
          <Button key="submit" type="primary" onClick={handleSubmit}>
            {currentTransaction ? 'Сохранить' : 'Добавить'}
          </Button>
        ]}
        width={450}
      >
        <Space direction="vertical" size="middle" style={{width: '100%'}}>
          <Select
            value={instrument}
            style={{width: '100%'}}
            options={[
              {label: 'USD/CAD', value: 'USD/CAD'},
              {label: 'EUR/USD', value: 'EUR/USD'}
            ]}
            onChange={setInstrument}
            defaultValue="USD/CAD"
          />

          <div>
            <InputNumber
              style={{width: '100%'}}
              placeholder="Сумма"
              formatter={value => `$ ${value}`.replace(/\B(?=(\d{3})+(?!\d))/g, ',')}
              parser={value =>
                value === null || value === void 0 ? void 0 : value.replace(/\$\s?|(,*)/g, '')
              }
              onChange={(e) => setAmount(e || 0)}
              min={0}
              value={amount}
              status={errors.amount ? "error" : undefined}
            />
            {errors.amount && (
              <Typography.Text type="danger" style={{fontSize: '12px'}}>
                {errors.amount}
              </Typography.Text>
            )}
          </div>

          {/*<div>
              <Space.Compact style={{ width: '100%' }}>
                <DatePicker
                  placeholder="Дата"
                  style={{ width: '100%' }}
                  showTime
                  format="DD.MM.YYYY HH:mm"
                  timeFormat="HH:mm"
                  onChange={(value, dateString) => {
                    console.log('Selected Time: ', value);
                    console.log('Formatted Selected Time: ', dateString);
                    setDate(value);
                  }}
                />
              </Space.Compact>
              {errors.date && (
                <Typography.Text type="danger" style={{ fontSize: '12px' }}>
                  {errors.date}
                </Typography.Text>
              )}
            </div>*/}
        </Space>
      </Modal>

      {/* Modal for confirming deletion */}
      <Modal
        title="Подтверждение удаления"
        open={isDeleteConfirmOpen}
        onCancel={handleCloseDeleteConfirm}
        footer={[
          <Button key="cancel" onClick={handleCloseDeleteConfirm}>
            Отмена
          </Button>,
          <Button key="delete" type="primary" danger onClick={handleDelete}>
            Удалить
          </Button>
        ]}
        width={450}
      >
        <Typography.Text style={{fontSize: '16px'}}>
          Вы уверены, что хотите удалить эту запись?
        </Typography.Text>
      </Modal>
    </>

  );
}