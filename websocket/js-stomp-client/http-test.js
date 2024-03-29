// const serverHost = ""; // Personal EC2
// const serverHost = ""; // Sinor EC2
const serverHost = "localhost";

async function postData(url = "", data = {}) {
  const response = await fetch(url, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify(data),
  });
  return response.json();
}

function postMember(
  memberName = "anonymous",
  memberProfile = "https://picsum.photos/200"
) {
  const memberRequestDto = {
    name: memberName,
    profile: memberProfile,
  };
  return postData(`http://${serverHost}:8080/member`, memberRequestDto);
}

function postBoard() {
  const boardRequestDto = {};
  return postData(`http://${serverHost}:8080/board`, boardRequestDto);
}

function postVote(boardId = 1) {
  const voteItemRequestDto = {
    content: "식당#1",
  };
  const voteRequestDto = {
    boardId: boardId,
    validUntil: "2023-12-27T16:56:39",
    isAnonymous: false,
    isMultiple: true,
    voteItems: [voteItemRequestDto],
  };
  return postData(`http://${serverHost}:8080/vote`, voteRequestDto);
}

function postVoteItem(voteId = 1) {
  const voteItemRequestDto = {
    voteId: voteId,
    content: "식당#2",
  };
  return postData(`http://${serverHost}:8080/vote/item`, voteItemRequestDto);
}

function postVoteLog(voteItemId = 1, memberId = 1) {
  const voteLogRequestDto = {
    voteItemId: voteItemId,
    memberId: memberId,
  };
  return postData(`http://${serverHost}:8080/vote/log`, voteLogRequestDto);
}

function getRandomInteger() {
  return parseInt(Math.random() * 999);
}

export {
  postBoard,
  postMember,
  postVote,
  postVoteItem,
  postVoteLog,
  getRandomInteger,
};
