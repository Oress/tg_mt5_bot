import {useEffect, useState} from "react";
import api from "../../api.js";
import {Card, Divider, Flex, Space, Spin, Table, Typography} from "antd";
import {formatCurrency} from "../../utils.js";

export function Mt5DataSection({userId, refreshTrigger}) {
  const [positions, setPositions] = useState([]);
  const [isLoading, setIsLoading] = useState(true);

  const fetchPositions = async () => {
    setIsLoading(true);
    try {
      const response = await api.get(`/users/${userId}/terminal-data`);
      setPositions(response.data.data || []);
    } catch (error) {
      console.error('Error fetching deposits:', error);
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    fetchPositions();
  }, [userId, refreshTrigger]);

  // type 0 - buy, 1 - sell
  const columns = [
    {
      title: 'Тип',
      dataIndex: 'type',
      key: 'type',
      width: 60,
      render: (type) => (
        <Typography.Text
          style={{
            fontWeight: 500,
            color: type === 0 ? '#52c41a' : '#ff4d4f'
          }}
        >
          {type === 0 ? 'Покупка' : 'Продажа'}
        </Typography.Text>
      ),
    },
    {
      title: 'Инструмент',
      key: 'symbol',
      dataIndex: 'symbol',
      width: 60,
    },
    {
      title: 'Цена (на сейчас)',
      key: 'priceCurrent',
      dataIndex: 'priceCurrent',
      width: 60,
    },
    {
      title: 'Нериализованный PnL (на сейчас)',
      key: 'profit',
      dataIndex: 'profit',
      width: 60,
      render: (profit) => (
        <Typography.Text>
          {formatCurrency(profit)}
        </Typography.Text>
      )
    },
    {
      title: 'Лоты',
      key: 'volume',
      dataIndex: 'volume',
      width: 60,
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
        }*/
  ];

  return (
    <>
      <Card style={{marginBottom: '24px'}}>
        <Space direction="vertical" size="middle" style={{width: '100%'}}>
          <div className="flex flex-col sm:flex-row justify-between items-center gap-2">
            <Typography.Title level={4} style={{margin: 0}}>
              Позиции MT5
            </Typography.Title>
          </div>
          <Divider style={{margin: 0}}/>

          {isLoading ? (
            <Flex justify="center" style={{padding: '40px 0'}}>
              <Spin size="large"/>
            </Flex>
          ) : positions.length > 0 ? (
            <Table
              style={{maxHeight: '400px', overflow: 'auto'}}
              columns={columns}
              dataSource={positions}
              rowKey="id"
              scroll={{x: 'max-content'}}
              pagination={false}
              size="small"
            />
          ) : (
            <Flex justify="center" style={{padding: '40px 0'}}>
              <Typography.Text type="secondary">
                Нет записей о позициях для этого клиента
              </Typography.Text>
            </Flex>
          )}
        </Space>
      </Card>
    </>
  );
}