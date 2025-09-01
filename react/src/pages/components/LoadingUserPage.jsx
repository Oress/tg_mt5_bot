import {Flex, Spin, theme} from "antd";

export function LoadingUserPage() {
  const {token} = theme.useToken();
  return (
    <div style={{minHeight: '100vh', backgroundColor: token.colorBgLayout}}>
      <Flex align="center" justify="center" style={{height: '100vh'}}>
        <Spin size="large"/>
      </Flex>
    </div>
  );
}